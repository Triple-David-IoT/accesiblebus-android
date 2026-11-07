"""Response models from contracts/openapi.yaml."""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class Trip(ApiModel):
    id: UUID
    vehicle_id: str
    driver_subject: str
    state: Literal["ACTIVE", "ENDED"]
    last_passed_sequence: int = Field(ge=0)


class StopVisit(ApiModel):
    id: UUID
    trip_id: UUID
    sequence: int = Field(ge=1)
    physical_stop_code: str


class ResetResult(ApiModel):
    trip: Trip
    stop_visits: list[StopVisit]


class ErrorResponse(ApiModel):
    code: str
    message: str
    current_resource: dict | None = None
