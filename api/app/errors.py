"""API errors with the response format defined in contracts/openapi.yaml."""

from fastapi import Request
from fastapi.responses import JSONResponse


class ApiError(Exception):
    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        current_resource: dict | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.current_resource = current_resource


def api_error_handler(_request: Request, error: ApiError) -> JSONResponse:
    content = {"code": error.code, "message": error.message}
    if error.current_resource is not None:
        content["currentResource"] = error.current_resource
    return JSONResponse(status_code=error.status_code, content=content)
