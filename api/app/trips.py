"""Trip progress: pass a stop and end the trip (B05)."""

from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header
from psycopg import Cursor

from . import db
from .auth import Actor, Role, current_actor
from .commands import FORBIDDEN, MESSAGE_SCHEMA_VERSION, NOT_FOUND, UNAUTHORIZED, change_state, run_command
from .errors import ApiError
from .models import AdvanceBody, ErrorResponse, Trip

router = APIRouter()

TRIP_COLUMNS = "id, vehicle_id, driver_subject, state, last_passed_sequence"


def trip_resource(row: dict) -> dict:
    return Trip(**row).model_dump(mode="json", by_alias=True)


def trip_event(cursor: Cursor, event_type: str, row: dict) -> None:
    """One event for the vehicle and for every owner of a request of this trip (K2)."""
    resource = trip_resource(row)
    event_id = uuid4()
    message = {
        "schemaVersion": MESSAGE_SCHEMA_VERSION,
        "eventId": str(event_id),
        "eventType": event_type,
        "tripId": resource["id"],
        "payload": resource,
    }
    cursor.execute(
        """
        SELECT DISTINCT r.owner_subject FROM assistance_request r
        JOIN stop_visit v ON v.id = r.stop_visit_id WHERE v.trip_id = %s
        """,
        (row["id"],),
    )
    topics = [f"vehicles/{row['vehicle_id']}/events"]
    topics += [f"users/{owner['owner_subject']}/events" for owner in cursor.fetchall()]
    for topic in topics:
        db.enqueue_event(cursor, event_id, event_type, row["id"], topic, message)


def lock_trip_for_driver(cursor: Cursor, trip_id: UUID, actor: Actor) -> dict:
    """Lock the trip, check the actor is its driver and that it is still active."""
    cursor.execute(f"SELECT {TRIP_COLUMNS} FROM trip WHERE id = %s FOR UPDATE", (trip_id,))
    trip = cursor.fetchone()
    if trip is None:
        raise ApiError(404, "NOT_FOUND", "Trip not found")
    if actor.role != Role.DRIVER or trip["driver_subject"] != actor.subject:
        raise ApiError(403, "FORBIDDEN", "Only the assigned driver can do this")
    if trip["state"] != "ACTIVE":
        raise ApiError(409, "TRIP_NOT_ACTIVE", "The trip has ended", trip_resource(trip))
    return trip


def end_trip(cursor: Cursor, trip_id: UUID) -> dict:
    """Close every open request as TRIP_ENDED, end the trip and emit trip.ended.

    The caller holds the trip lock. The demo reset uses it too.
    """
    cursor.execute(
        """
        SELECT r.id FROM assistance_request r JOIN stop_visit v ON v.id = r.stop_visit_id
        WHERE v.trip_id = %s AND r.state IN ('PENDING', 'ACCEPTED')
        ORDER BY r.id FOR UPDATE OF r
        """,
        (trip_id,),
    )
    for request in cursor.fetchall():
        change_state(cursor, request["id"], "EXPIRED", "TRIP_ENDED", "request.expired")
    cursor.execute(
        f"UPDATE trip SET state = 'ENDED', updated_at = now() WHERE id = %s RETURNING {TRIP_COLUMNS}",
        (trip_id,),
    )
    ended = cursor.fetchone()
    trip_event(cursor, "trip.ended", ended)
    return trip_resource(ended)


TRIP_RESPONSES = {
    401: UNAUTHORIZED,
    403: FORBIDDEN,
    404: NOT_FOUND,
    409: {"model": ErrorResponse, "description": "Key reused, trip ended or progress out of date"},
}


@router.post("/trips/{trip_id}/advance", response_model=Trip, tags=["Trips"], responses=TRIP_RESPONSES)
def advance_trip(
    trip_id: UUID,
    body: AdvanceBody,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Assigned driver only. Passes the next stop and expires its pending requests."""

    def run(cursor: Cursor) -> dict:
        trip = lock_trip_for_driver(cursor, trip_id, actor)
        cursor.execute("SELECT count(*) AS total FROM stop_visit WHERE trip_id = %s", (trip_id,))
        total = cursor.fetchone()["total"]
        if body.from_sequence != trip["last_passed_sequence"] or body.from_sequence >= total:
            raise ApiError(409, "VERSION_CONFLICT", "The trip has moved", trip_resource(trip))
        passed = body.from_sequence + 1
        cursor.execute(
            """
            SELECT r.id FROM assistance_request r JOIN stop_visit v ON v.id = r.stop_visit_id
            WHERE v.trip_id = %s AND v.sequence = %s AND r.state = 'PENDING'
            ORDER BY r.id FOR UPDATE OF r
            """,
            (trip_id, passed),
        )
        for request in cursor.fetchall():
            change_state(cursor, request["id"], "EXPIRED", "STOP_PASSED", "request.expired")
        cursor.execute(
            f"UPDATE trip SET last_passed_sequence = %s, updated_at = now() WHERE id = %s RETURNING {TRIP_COLUMNS}",
            (passed, trip_id),
        )
        moved = cursor.fetchone()
        trip_event(cursor, "trip.progressed", moved)
        return trip_resource(moved)

    path = f"/trips/{trip_id}/advance"
    result, _ = run_command(actor, idempotency_key, "advance_trip", path, body.model_dump(mode="json", by_alias=True), run)
    return result


@router.post("/trips/{trip_id}/end", response_model=Trip, tags=["Trips"], responses=TRIP_RESPONSES)
def end_trip_route(
    trip_id: UUID,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Assigned driver only. Closes every open request as TRIP_ENDED."""

    def run(cursor: Cursor) -> dict:
        lock_trip_for_driver(cursor, trip_id, actor)
        return end_trip(cursor, trip_id)

    result, _ = run_command(actor, idempotency_key, "end_trip", f"/trips/{trip_id}/end", {}, run)
    return result