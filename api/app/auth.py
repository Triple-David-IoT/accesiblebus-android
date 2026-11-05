"""Identity from the fixed K7 demo tokens. This is laboratory access, not real security."""

from dataclasses import dataclass
from enum import StrEnum
from typing import Annotated

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .errors import ApiError


class Role(StrEnum):
    PASSENGER = "passenger"
    DRIVER = "driver"
    OPERATOR = "operator"


@dataclass(frozen=True)
class Actor:
    subject: str
    role: Role
    vehicle_id: str | None = None
    terminal_id: str | None = None


TOKENS = {
    "demo-pasajero-1": Actor(subject="passenger-1", role=Role.PASSENGER),
    "demo-conductor-1": Actor(
        subject="driver-1",
        role=Role.DRIVER,
        vehicle_id="BUS-DEMO-01",
        terminal_id="hmi-demo-01",
    ),
    "demo-operador-1": Actor(subject="operator-1", role=Role.OPERATOR),
}

bearer = HTTPBearer(scheme_name="demoToken", auto_error=False)


def current_actor(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> Actor:
    actor = TOKENS.get(credentials.credentials) if credentials else None
    if actor is None:
        raise ApiError(401, "UNAUTHORIZED", "Missing or unknown token")
    return actor
