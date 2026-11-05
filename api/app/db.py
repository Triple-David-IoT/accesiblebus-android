"""Connection pool and shared database helpers."""

from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path
from uuid import UUID

from psycopg import Cursor
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from .settings import Settings

# Increase this when a new schema_00N.sql file is added.
SCHEMA_VERSION = 1

_SCHEMA_FILE = Path(__file__).with_name("schema.sql")
_pool: ConnectionPool | None = None


def open_pool(settings: Settings) -> None:
    global _pool
    _pool = ConnectionPool(
        settings.database_url,
        min_size=1,
        max_size=5,
        timeout=5,
        kwargs={"row_factory": dict_row},
        open=False,
    )
    _pool.open(wait=True, timeout=30)


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None


@contextmanager
def transaction() -> Iterator[Cursor]:
    """Commit when the block ends normally; roll back if it raises."""
    if _pool is None:
        raise RuntimeError("The connection pool is not open")
    with _pool.connection() as connection:
        with connection.cursor() as cursor:
            yield cursor


def apply_schema() -> None:
    with transaction() as cursor:
        cursor.execute(_SCHEMA_FILE.read_text(encoding="utf-8"))


def enqueue_event(
    cursor: Cursor,
    event_id: UUID,
    event_type: str,
    trip_id: UUID,
    topic: str,
    payload: dict,
    request_id: UUID | None = None,
) -> None:
    """Add one outbox row. Call it inside the transaction that changes the data."""
    cursor.execute(
        """
        INSERT INTO outbox_event (event_id, event_type, request_id, trip_id, topic, payload)
        VALUES (%s, %s, %s, %s, %s, %s)
        """,
        (event_id, event_type, request_id, trip_id, topic, Jsonb(payload)),
    )
