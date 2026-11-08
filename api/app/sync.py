"""GET /sync: one consistent snapshot of a trip and its requests (A06).

Clients call it on start, after reconnecting and when the user presses Refresh, and then
merge it with the MQTT events they received meanwhile (K2).
"""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Query
from fastapi.exceptions import RequestValidationError

from . import db
from .auth import Actor, Role, current_actor
from .commands import FORBIDDEN, REQUEST_SELECT, UNAUTHORIZED, to_request
from .demo import VEHICLE_ID
from .errors import ApiError
from .models import Snapshot, StopVisit, Trip

router = APIRouter()


def read_snapshot(vehicle_id: str, owner_subject: str | None) -> Snapshot:
    with db.transaction() as cursor:
        # All reads below see the database at the same instant, even if commands run meanwhile.
        cursor.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")

        # The active trip; with none, the most recent ended trip, so clients can block new requests.
        cursor.execute(
            """
            SELECT id, vehicle_id, driver_subject, state, last_passed_sequence
            FROM trip WHERE vehicle_id = %s
            ORDER BY (state = 'ACTIVE') DESC, created_at DESC
            LIMIT 1
            """,
            (vehicle_id,),
        )
        trip = cursor.fetchone()
        if trip is None:
            return Snapshot(trip=None, stop_visits=[], requests=[])

        cursor.execute(
            "SELECT id, trip_id, sequence, physical_stop_code FROM stop_visit WHERE trip_id = %s ORDER BY sequence",
            (trip["id"],),
        )
        visits = cursor.fetchall()

        # Requests of this trip and of the ended trip before it, so a client that was offline
        # during a reset can still see how its old requests were closed.
        cursor.execute(
            """
            SELECT id FROM trip
            WHERE vehicle_id = %s AND state = 'ENDED' AND id <> %s
              AND created_at < (SELECT created_at FROM trip WHERE id = %s)
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (vehicle_id, trip["id"], trip["id"]),
        )
        previous = cursor.fetchone()
        trip_ids = [trip["id"]] + ([previous["id"]] if previous else [])

        query = REQUEST_SELECT + " WHERE v.trip_id = ANY(%s)"
        params: list = [trip_ids]
        if owner_subject is not None:
            query += " AND r.owner_subject = %s"
            params.append(owner_subject)
        cursor.execute(query + " ORDER BY (v.trip_id = %s) DESC, v.sequence, r.created_at", [*params, trip["id"]])
        requests = [to_request(row) for row in cursor.fetchall()]

    return Snapshot(trip=Trip(**trip), stop_visits=[StopVisit(**v) for v in visits], requests=requests)


@router.get(
    "/sync",
    response_model=Snapshot,
    tags=["Sync"],
    responses={401: UNAUTHORIZED, 403: FORBIDDEN},
)
def sync(
    actor: Annotated[Actor, Depends(current_actor)],
    scope: Annotated[Literal["mine", "vehicle"], Query()],
    vehicle_id: Annotated[str | None, Query(alias="vehicleId")] = None,
) -> Snapshot:
    if scope == "mine":
        if actor.role != Role.PASSENGER:
            raise ApiError(403, "FORBIDDEN", "scope=mine is for passengers")
        # The laboratory has a single demo vehicle, so a passenger's trip is always that one.
        return read_snapshot(VEHICLE_ID, actor.subject)

    if not vehicle_id:
        # Same 422 format as FastAPI's own validation errors, as K1 defines.
        raise RequestValidationError(
            [{"loc": ("query", "vehicleId"), "msg": "Field required when scope is vehicle", "type": "missing"}]
        )
    allowed = actor.role == Role.OPERATOR or (actor.role == Role.DRIVER and actor.vehicle_id == vehicle_id)
    if not allowed:
        raise ApiError(403, "FORBIDDEN", "Only the assigned driver or the operator can read this vehicle")
    return read_snapshot(vehicle_id, None)
