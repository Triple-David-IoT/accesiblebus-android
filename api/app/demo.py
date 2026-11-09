"""Demo scenario: the active trip and its reset."""

import json
import logging
from pathlib import Path
from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, Header
from psycopg import Cursor

from . import db
from .auth import Actor, Role, current_actor
from .commands import run_command
from .trips import end_trip
from .errors import ApiError
from .models import ErrorResponse, ResetResult, StopVisit, Trip
from .settings import Settings

logger = logging.getLogger(__name__)
router = APIRouter(tags=["Demo"])

VEHICLE_ID = "BUS-DEMO-01"
DRIVER_SUBJECT = "driver-1"


def load_stop_codes(manifest_path: str) -> list[str]:
    """Return the stop codes in trip order. Fail clearly if the manifest is wrong."""
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    stops = sorted(manifest["stops"], key=lambda stop: stop["sequence"])
    if [stop["sequence"] for stop in stops] != [1, 2, 3, 4]:
        raise ValueError("The manifest must define stops with sequence 1 to 4")
    return [stop["physicalStopCode"] for stop in stops]


def create_trip(cursor: Cursor, stop_codes: list[str]) -> tuple[Trip, list[StopVisit]]:
    trip_id = uuid4()
    cursor.execute(
        """
        INSERT INTO trip (id, vehicle_id, driver_subject, state)
        VALUES (%s, %s, %s, 'ACTIVE')
        """,
        (trip_id, VEHICLE_ID, DRIVER_SUBJECT),
    )
    visits = []
    for sequence, code in enumerate(stop_codes, start=1):
        visit_id = uuid4()
        cursor.execute(
            """
            INSERT INTO stop_visit (id, trip_id, sequence, physical_stop_code)
            VALUES (%s, %s, %s, %s)
            """,
            (visit_id, trip_id, sequence, code),
        )
        visits.append(
            StopVisit(id=visit_id, trip_id=trip_id, sequence=sequence, physical_stop_code=code)
        )
    trip = Trip(
        id=trip_id,
        vehicle_id=VEHICLE_ID,
        driver_subject=DRIVER_SUBJECT,
        state="ACTIVE",
        last_passed_sequence=0,
    )
    return trip, visits


def ensure_scenario(settings: Settings) -> None:
    """Create the demo trip only if the vehicle has no active trip."""
    stop_codes = load_stop_codes(settings.manifest_path)
    with db.transaction() as cursor:
        cursor.execute(
            "SELECT id FROM trip WHERE vehicle_id = %s AND state = 'ACTIVE'",
            (VEHICLE_ID,),
        )
        active = cursor.fetchone()
        if active:
            logger.info("Keeping active trip %s", active["id"])
            return
        trip, _ = create_trip(cursor, stop_codes)
        logger.info("Created demo trip %s", trip.id)


def end_active_trip(cursor: Cursor) -> None:
    """End the active demo trip: its open requests expire and trip.ended is emitted (B05)."""
    cursor.execute("SELECT id FROM trip WHERE vehicle_id = %s AND state = 'ACTIVE' FOR UPDATE", (VEHICLE_ID,))
    for trip in cursor.fetchall():
        end_trip(cursor, trip["id"])
        logger.info("Ended demo trip %s", trip["id"])


@router.post(
    "/demo/reset",
    response_model=ResetResult,
    responses={
        401: {"model": ErrorResponse, "description": "Missing or unknown token"},
        403: {"model": ErrorResponse, "description": "Only the operator can reset the demo"},
        409: {"model": ErrorResponse, "description": "The key was already used for another command"},
    },
)
def reset_demo(
    actor: Annotated[Actor, Depends(current_actor)],
    idempotency_key: Annotated[str, Header(min_length=1)],
) -> dict:
    if actor.role != Role.OPERATOR:
        raise ApiError(403, "FORBIDDEN", "Only the operator can reset the demo")
    stop_codes = load_stop_codes(Settings.from_env().manifest_path)

    def reset(cursor: Cursor) -> dict:
        end_active_trip(cursor)
        trip, visits = create_trip(cursor, stop_codes)
        return ResetResult(trip=trip, stop_visits=visits).model_dump(mode="json", by_alias=True)

    result, _ = run_command(actor, idempotency_key, "reset_demo", "/demo/reset", {}, reset)
    return result
