# API startup contract (K4)

The API runs as one Uvicorn process with `--workers 1`. That process also runs two background threads: the outbox publisher and the receipts subscriber. This document defines how they start and stop, so each one can be built separately.

## Files

| File | Owner | Task | Purpose |
| --- | --- | --- | --- |
| `settings.py` | A | K4 | Configuration from environment variables |
| `main.py` | A | A03 | Startup, shutdown and routes |
| `db.py` | A | A03 | Connection pool and shared database helpers |
| `outbox.py` | C | C03 | Publishes pending outbox rows to MQTT |
| `receipts.py` | B | B06 | Records HMI receipts from MQTT |

`outbox.py` and `receipts.py` start as placeholders that only write to the log, so the API can boot before C03 and B06 exist. C03 and B06 replace their bodies and keep the signatures.

## Thread interface

```python
def start_outbox(settings: Settings) -> Stoppable: ...
def start_receipts(settings: Settings) -> Stoppable: ...
```

`Stoppable` is any object with a `stop() -> None` method.

### Rules

- **Return quickly.** `start_*` starts the thread and returns. It must not wait for the broker or raise if the broker is down, because the REST API has to keep responding (TD05). The thread retries on its own.
- **Use your own resources.** Each thread opens its own database connection and MQTT client, with the K7 client IDs `api-outbox` and `api-receipts`.
- **Never touch the event loop.** Use a normal `threading.Thread(daemon=True)`. Waiting for a `PUBACK` must not block REST requests.
- **Survive errors.** Log the error, roll back and try again. An exception must not end the thread.
- **Stop within a few seconds.** `stop()` asks the thread to end and waits for it with a timeout, so shutdown never hangs.
- **Share no mutable state.** The threads and the API communicate only through the database.

## Startup and shutdown order

Startup, in the FastAPI `lifespan`:

1. Apply `schema.sql`.
2. Create the demo trip if there is no active trip (`demo.ensure_scenario()`).
3. Start the outbox publisher.
4. Start the receipts subscriber.
5. Start serving requests.

Shutdown:

1. Stop serving requests.
2. Stop the receipts subscriber.
3. Stop the outbox publisher.

Outbox rows that are still pending stay in the database, and the publisher resumes them after the next start.

## Configuration

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `DATABASE_URL` | Yes | | PostgreSQL connection string |
| `MQTT_HOST` | Yes | | Broker host |
| `MQTT_PORT` | No | `1883` | Broker port |
| `MANIFEST_PATH` | No | `/app/contracts/manifest.json` | Stop manifest, mounted from `contracts/` |

To add a variable, add it to `settings.py` and `docker-compose.yml` in the same pull request.

## Writing outbox events

Commands (A05, A07, B05) and the receipts subscriber (B06) all write outbox rows. They must use one shared helper from `db.py`, inside the transaction that changes the data:

```python
def enqueue_event(
    cur,
    event_id: UUID,
    event_type: str,
    trip_id: UUID,
    topic: str,
    payload: dict,
    request_id: UUID | None = None,
) -> None: ...
```

- Call it once per destination topic, with the same `event_id`.
- `payload` is the full MQTT message, which the publisher sends unchanged.
- `request_id` is required for `request.*` events and must be `None` for `trip.*` events.

The helper is added in A03. Message formats belong to the MQTT contract (K2).

### Outbox publisher

`OUTBOX_PUBLISH_DELAY_MS` is an optional delay, in milliseconds,
between selecting an outbox event and publishing it.

It defaults to `0` and can be used in failure scenarios to test
the outbox behavior while the API or MQTT broker is interrupted.