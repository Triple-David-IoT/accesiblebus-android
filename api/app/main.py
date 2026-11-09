"""API entry point. README.md describes the startup and shutdown order."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import db
from .commands import router as commands_router
from .demo import ensure_scenario
from .demo import router as demo_router
from .errors import ApiError, api_error_handler
from .outbox import start_outbox
from .receipts import start_receipts
from .settings import Settings
from .sync import router as sync_router
from .trips import router as trips_router

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    settings = Settings.from_env()
    db.open_pool(settings)
    try:
        db.apply_schema()
        logger.info("Schema version %s applied", db.SCHEMA_VERSION)
        ensure_scenario(settings)
        outbox = start_outbox(settings)
        receipts = start_receipts(settings)
        try:
            yield
        finally:
            receipts.stop()
            outbox.stop()
    finally:
        db.close_pool()


app = FastAPI(title="AccessibleBus API", version="0.1.0", lifespan=lifespan)
app.add_exception_handler(ApiError, api_error_handler)
app.include_router(commands_router)
app.include_router(demo_router)
app.include_router(sync_router)
app.include_router(trips_router)


@app.get("/health", tags=["Health"])
def health() -> JSONResponse:
    """Report whether the API can reach the database, and the schema version."""
    try:
        with db.transaction() as cursor:
            cursor.execute("SELECT 1")
    except Exception:
        logger.exception("Health check failed")
        return JSONResponse(
            status_code=503,
            content={"status": "unavailable", "schemaVersion": db.SCHEMA_VERSION},
        )
    return JSONResponse(content={"status": "ok", "schemaVersion": db.SCHEMA_VERSION})
