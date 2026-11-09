"""Response models from contracts/openapi.yaml."""

from datetime import datetime
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


class AssistanceRequest(ApiModel):
    id: UUID
    trip_id: UUID
    vehicle_id: str
    owner_subject: str
    stop_visit_id: UUID
    sequence: int = Field(ge=1)
    physical_stop_code: str
    assistance_type: Literal["RAMP_BOARDING"]
    state: Literal["PENDING", "ACCEPTED", "REJECTED", "CANCELLED", "COMPLETED", "EXPIRED"]
    version: int = Field(ge=1)
    received: bool
    created_event_id: UUID
    close_reason: str | None
    created_at: datetime
    updated_at: datetime


class CreateRequestBody(ApiModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")

    stop_visit_id: UUID
    assistance_type: Literal["RAMP_BOARDING"]


class VersionBody(ApiModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")

    expected_version: int = Field(ge=1)


class RejectBody(VersionBody):
    reason: Literal["RAMP_UNAVAILABLE", "UNSAFE_STOP", "OTHER"]


class AdvanceBody(ApiModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")

    from_sequence: int = Field(ge=0)


class Snapshot(ApiModel):
    trip: Trip | None
    stop_visits: list[StopVisit]
    requests: list[AssistanceRequest]


class ErrorResponse(ApiModel):
    code: str
    message: str
    current_resource: dict | None = None
