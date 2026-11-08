"""Assistance request commands and reads (A05).

Every command runs inside run_command: one transaction that reserves the idempotency key,
applies the change, writes the outbox rows and stores the response. Either all of it is
saved, or none of it is.
"""

import hashlib
import json
from collections.abc import Callable
from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header, Response
from psycopg import Cursor
from psycopg.types.json import Jsonb

from . import db
from .auth import Actor, Role, current_actor
from .errors import ApiError
from .models import AssistanceRequest, CreateRequestBody, ErrorResponse, RejectBody, StopVisit, VersionBody

router = APIRouter()
MESSAGE_SCHEMA_VERSION = 3

UNAUTHORIZED = {"model": ErrorResponse, "description": "Missing or unknown token"}
FORBIDDEN = {"model": ErrorResponse, "description": "The token's role or identity cannot do this"}
NOT_FOUND = {"model": ErrorResponse, "description": "The resource does not exist"}

# The request resource of K1, with the driver used for permission checks.
REQUEST_SELECT = """
    SELECT r.id, v.trip_id, t.vehicle_id, t.driver_subject, r.owner_subject,
           r.stop_visit_id, v.sequence, v.physical_stop_code, r.assistance_type,
           r.state, r.version, r.created_event_id, r.close_reason,
           r.created_at, r.updated_at,
           EXISTS (
               SELECT 1 FROM terminal_receipt tr
               WHERE tr.created_event_id = r.created_event_id
           ) AS received
    FROM assistance_request r
    JOIN stop_visit v ON v.id = r.stop_visit_id
    JOIN trip t ON t.id = v.trip_id
"""


def to_request(row: dict) -> AssistanceRequest:
    return AssistanceRequest(**{k: v for k, v in row.items() if k != "driver_subject"})


def run_command(
    actor: Actor,
    key: str,
    operation: str,
    path: str,
    body: dict,
    fn: Callable[[Cursor], dict],
) -> tuple[dict, bool]:
    """Run a command once per actor and key.

    Returns the response and whether it was stored by an earlier call with the same key.
    If fn raises, the transaction rolls back, including the key, so a retry runs again.
    """
    canonical = json.dumps({"operation": operation, "path": path, "body": body}, sort_keys=True)
    command_hash = hashlib.sha256(canonical.encode()).hexdigest()
    with db.transaction() as cursor:
        # A second call with the same key waits here until the first transaction ends.
        # ON CONFLICT DO NOTHING turns the duplicate into "no row" instead of an error,
        # so this transaction stays usable and can read the stored response.
        cursor.execute(
            """
            INSERT INTO processed_command (actor, idempotency_key, operation, path, command_hash)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (actor, idempotency_key) DO NOTHING
            RETURNING actor
            """,
            (actor.subject, key, operation, path, command_hash),
        )
        if cursor.fetchone() is None:
            cursor.execute(
                """
                SELECT command_hash, response FROM processed_command
                WHERE actor = %s AND idempotency_key = %s
                """,
                (actor.subject, key),
            )
            stored = cursor.fetchone()
            if stored["command_hash"] != command_hash:
                raise ApiError(409, "IDEMPOTENCY_CONFLICT", "This key was already used for another command")
            return stored["response"], True
        response = fn(cursor)
        cursor.execute(
            "UPDATE processed_command SET response = %s WHERE actor = %s AND idempotency_key = %s",
            (Jsonb(response), actor.subject, key),
        )
        return response, False


def create_request(cursor: Cursor, actor: Actor, body: CreateRequestBody) -> dict:
    # Lock the trip first: create, advance and end are ordered through this row.
    cursor.execute(
        """
        SELECT t.id AS trip_id, t.vehicle_id, t.state, t.last_passed_sequence, v.sequence
        FROM stop_visit v JOIN trip t ON t.id = v.trip_id
        WHERE v.id = %s
        FOR UPDATE OF t
        """,
        (body.stop_visit_id,),
    )
    visit = cursor.fetchone()
    if visit is None:
        raise ApiError(404, "NOT_FOUND", "Stop visit not found")
    if visit["state"] != "ACTIVE":
        raise ApiError(409, "TRIP_NOT_ACTIVE", "The trip has ended")
    if visit["sequence"] <= visit["last_passed_sequence"]:
        raise ApiError(409, "VISIT_ALREADY_PASSED", "The bus has already passed this stop")

    cursor.execute(
        REQUEST_SELECT
        + " WHERE r.owner_subject = %s AND r.stop_visit_id = %s AND r.state IN ('PENDING', 'ACCEPTED')",
        (actor.subject, body.stop_visit_id),
    )
    active = cursor.fetchone()
    if active:
        current = to_request(active).model_dump(mode="json", by_alias=True)
        raise ApiError(409, "ACTIVE_REQUEST_EXISTS", "You already have an active request for this stop", current)

    request_id, event_id = uuid4(), uuid4()
    cursor.execute(
        """
        INSERT INTO assistance_request (id, owner_subject, stop_visit_id, assistance_type, state, created_event_id)
        VALUES (%s, %s, %s, %s, 'PENDING', %s)
        """,
        (request_id, actor.subject, body.stop_visit_id, body.assistance_type, event_id),
    )
    cursor.execute(REQUEST_SELECT + " WHERE r.id = %s", (request_id,))
    resource = to_request(cursor.fetchone()).model_dump(mode="json", by_alias=True)
    # The created event reuses created_event_id, which the HMI acknowledges.
    request_event(cursor, "request.created", resource, event_id)
    return resource


@router.post(
    "/requests",
    response_model=AssistanceRequest,
    status_code=201,
    tags=["Requests"],
    responses={
        200: {"model": AssistanceRequest, "description": "Repeated command; returns the stored response"},
        401: UNAUTHORIZED,
        403: FORBIDDEN,
        404: NOT_FOUND,
        409: {
            "model": ErrorResponse,
            "description": "Key used for another command, trip ended, stop passed, or an active request already exists",
        },
    },
)
def post_request(
    body: CreateRequestBody,
    response: Response,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    if actor.role != Role.PASSENGER:
        raise ApiError(403, "FORBIDDEN", "Only a passenger can create a request")
    result, repeated = run_command(
        actor,
        idempotency_key,
        "create_request",
        "/requests",
        body.model_dump(mode="json", by_alias=True),
        lambda cursor: create_request(cursor, actor, body),
    )
    if repeated:
        response.status_code = 200
    return result


def request_event(cursor: Cursor, event_type: str, resource: dict, event_id: UUID | None = None) -> None:
    """Write one request event for the owner and the vehicle, with the full resource (K2)."""
    event_id = event_id or uuid4()
    message = {
        "schemaVersion": MESSAGE_SCHEMA_VERSION,
        "eventId": str(event_id),
        "eventType": event_type,
        "requestId": resource["id"],
        "tripId": resource["tripId"],
        "version": resource["version"],
        "payload": resource,
    }
    for topic in (f"users/{resource['ownerSubject']}/events", f"vehicles/{resource['vehicleId']}/events"):
        db.enqueue_event(cursor, event_id, event_type, resource["tripId"], topic, message, resource["id"])


def change_state(cursor: Cursor, request_id: UUID, new_state: str, reason: str | None, event_type: str) -> dict:
    """Apply a state change that was already validated, raise the version and write its event.

    The caller must hold the trip and request locks. B05 reuses it to close requests on
    advance and end, with EXPIRED and STOP_PASSED or TRIP_ENDED.
    """
    cursor.execute(
        """
        UPDATE assistance_request
        SET state = %s, close_reason = %s, version = version + 1, updated_at = now()
        WHERE id = %s
        """,
        (new_state, reason, request_id),
    )
    cursor.execute(REQUEST_SELECT + " WHERE r.id = %s", (request_id,))
    resource = to_request(cursor.fetchone()).model_dump(mode="json", by_alias=True)
    request_event(cursor, event_type, resource)
    return resource


# Who may run each transition, from which states, and what it produces. "done" is the word
# used in the INVALID_TRANSITION message.
TRANSITIONS = {
    "accept": {"who": "driver", "from": {"PENDING"}, "to": "ACCEPTED", "event": "request.accepted", "done": "accepted"},
    "reject": {"who": "driver", "from": {"PENDING"}, "to": "REJECTED", "event": "request.rejected", "done": "rejected"},
    "cancel": {
        "who": "owner", "from": {"PENDING", "ACCEPTED"}, "to": "CANCELLED", "event": "request.cancelled", "done": "cancelled",
    },
    "complete": {"who": "driver", "from": {"ACCEPTED"}, "to": "COMPLETED", "event": "request.completed", "done": "completed"},
}


def transition(
    actor: Actor, key: str, request_id: UUID, operation: str, body: VersionBody, reason: str | None = None
) -> dict:
    rule = TRANSITIONS[operation]

    def run(cursor: Cursor) -> dict:
        cursor.execute(
            """
            SELECT v.trip_id FROM assistance_request r JOIN stop_visit v ON v.id = r.stop_visit_id
            WHERE r.id = %s
            """,
            (request_id,),
        )
        found = cursor.fetchone()
        if found is None:
            raise ApiError(404, "NOT_FOUND", "Request not found")
        # Same lock order as every command: the trip first, then the request.
        cursor.execute("SELECT state, driver_subject FROM trip WHERE id = %s FOR UPDATE", (found["trip_id"],))
        trip = cursor.fetchone()
        cursor.execute("SELECT 1 FROM assistance_request WHERE id = %s FOR UPDATE", (request_id,))
        cursor.execute(REQUEST_SELECT + " WHERE r.id = %s", (request_id,))
        row = cursor.fetchone()
        current = to_request(row).model_dump(mode="json", by_alias=True)

        if rule["who"] == "driver":
            allowed = actor.role == Role.DRIVER and trip["driver_subject"] == actor.subject
        else:
            allowed = actor.role == Role.PASSENGER and row["owner_subject"] == actor.subject
        if not allowed:
            raise ApiError(403, "FORBIDDEN", f"You cannot {operation} this request")
        if trip["state"] != "ACTIVE":
            raise ApiError(409, "TRIP_NOT_ACTIVE", "The trip has ended", current)
        if row["version"] != body.expected_version:
            raise ApiError(409, "VERSION_CONFLICT", "The request has changed", current)
        if row["state"] not in rule["from"]:
            raise ApiError(409, "INVALID_TRANSITION", f"The request is {row['state']}, so it cannot be {rule['done']}", current)
        return change_state(cursor, request_id, rule["to"], reason, rule["event"])

    path = f"/requests/{request_id}/{operation}"
    result, _ = run_command(actor, key, operation, path, body.model_dump(mode="json", by_alias=True), run)
    return result


TRANSITION_RESPONSES = {
    401: UNAUTHORIZED,
    403: FORBIDDEN,
    404: NOT_FOUND,
    409: {
        "model": ErrorResponse,
        "description": "Key used for another command, trip ended, stale version or invalid transition",
    },
}


@router.post("/requests/{request_id}/accept", response_model=AssistanceRequest, tags=["Requests"], responses=TRANSITION_RESPONSES)
def accept_request(
    request_id: UUID,
    body: VersionBody,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Assigned driver only. PENDING to ACCEPTED."""
    return transition(actor, idempotency_key, request_id, "accept", body)


@router.post("/requests/{request_id}/reject", response_model=AssistanceRequest, tags=["Requests"], responses=TRANSITION_RESPONSES)
def reject_request(
    request_id: UUID,
    body: RejectBody,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Assigned driver only. PENDING to REJECTED, with a reason from the fixed list."""
    return transition(actor, idempotency_key, request_id, "reject", body, body.reason)


@router.post("/requests/{request_id}/cancel", response_model=AssistanceRequest, tags=["Requests"], responses=TRANSITION_RESPONSES)
def cancel_request(
    request_id: UUID,
    body: VersionBody,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Owner only. PENDING or ACCEPTED to CANCELLED."""
    return transition(actor, idempotency_key, request_id, "cancel", body)


@router.post("/requests/{request_id}/complete", response_model=AssistanceRequest, tags=["Requests"], responses=TRANSITION_RESPONSES)
def complete_request(
    request_id: UUID,
    body: VersionBody,
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    """Assigned driver only. ACCEPTED to COMPLETED while the trip is active, even if the stop was passed."""
    return transition(actor, idempotency_key, request_id, "complete", body)


@router.get(
    "/requests/{request_id}",
    response_model=AssistanceRequest,
    tags=["Requests"],
    responses={401: UNAUTHORIZED, 403: FORBIDDEN, 404: NOT_FOUND},
)
def get_request(request_id: UUID, actor: Annotated[Actor, Depends(current_actor)]) -> AssistanceRequest:
    with db.transaction() as cursor:
        cursor.execute(REQUEST_SELECT + " WHERE r.id = %s", (request_id,))
        row = cursor.fetchone()
    if row is None:
        raise ApiError(404, "NOT_FOUND", "Request not found")
    allowed = (
        actor.role == Role.OPERATOR
        or row["owner_subject"] == actor.subject
        or row["driver_subject"] == actor.subject
    )
    if not allowed:
        raise ApiError(403, "FORBIDDEN", "You cannot read this request")
    return to_request(row)


@router.get(
    "/trips/{trip_id}/stop-visits",
    response_model=list[StopVisit],
    tags=["Trips"],
    responses={401: UNAUTHORIZED, 404: NOT_FOUND},
)
def get_stop_visits(trip_id: UUID, actor: Annotated[Actor, Depends(current_actor)]) -> list[dict]:
    with db.transaction() as cursor:
        cursor.execute("SELECT 1 FROM trip WHERE id = %s", (trip_id,))
        if cursor.fetchone() is None:
            raise ApiError(404, "NOT_FOUND", "Trip not found")
        cursor.execute(
            "SELECT id, trip_id, sequence, physical_stop_code FROM stop_visit WHERE trip_id = %s ORDER BY sequence",
            (trip_id,),
        )
        return cursor.fetchall()
