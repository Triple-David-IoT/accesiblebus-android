# API: startup and background threads

The API runs as one Uvicorn process with `--workers 1`. That process also runs two background threads: the outbox publisher and the receipts subscriber. This document describes how they start and stop.

## Files

| File | Purpose |
| --- | --- |
| `settings.py` | Configuration from environment variables |
| `main.py` | Startup, shutdown and routes |
| `db.py` | Connection pool and shared database helpers |
| `schema.sql` | Tables, constraints and the outbox |
| `models.py` | Request and response bodies |
| `errors.py` | One error format for every route |
| `auth.py` | Fixed demo tokens and the identity behind each one |
| `commands.py` | Assistance request commands and reads |
| `sync.py` | `GET /sync`, one consistent snapshot of a trip and its requests |
| `trips.py` | Trip progress: pass a stop and end the trip |
| `demo.py` | Demo scenario and reset |
| `outbox.py` | Publishes pending outbox rows to MQTT |
| `receipts.py` | Records HMI receipts from MQTT |

## Thread interface

```python
def start_outbox(settings: Settings) -> Stoppable: ...
def start_receipts(settings: Settings) -> Stoppable: ...
```

`Stoppable` is any object with a `stop() -> None` method.

### Rules

- **Return quickly.** `start_*` starts the thread and returns. It must not wait for the broker or raise if the broker is down, because the REST API has to keep responding. The thread retries on its own.
- **Use your own resources.** Each thread has its own MQTT client, with the fixed client IDs `api-outbox` and `api-receipts`. The outbox publisher opens its own database connection, because it keeps a transaction open while it waits for each `PUBACK`. The receipts subscriber may use the shared pool (`db.transaction()`), because each receipt is one short transaction. If the pool stays busy past its timeout, the receipt is logged and dropped, and the HMI sends it again after its next `/sync`.
- **Never touch the event loop.** Use a normal `threading.Thread(daemon=True)`. Waiting for a `PUBACK` must not block REST requests.
- **Survive errors.** Log the error, roll back and try again. An exception must not end the thread.
- **Stop within a few seconds.** `stop()` asks the thread to end and waits for it with a timeout, so shutdown never hangs.
- **Share no mutable state.** The threads and the API communicate only through the database.

## Startup and shutdown order

Startup, in the FastAPI `lifespan`:

1. Apply `schema.sql`.
2. Create the demo trip if there is no active trip (`demo.ensure_scenario(settings)`).
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
| `OUTBOX_PUBLISH_DELAY_MS` | No | `0` | Delay between the `PUBACK` and the mark, for failure tests only |

A new variable goes in `docker-compose.yml` and is read in `settings.py`.

## Writing outbox events

The commands and the receipts subscriber all write outbox rows. They use one shared helper from `db.py`, inside the transaction that changes the data:

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

Message formats are defined in the MQTT contract, `contracts/mqtt.md`.

### Outbox publisher

Each event is published with QoS 1. Once the broker confirms it (`PUBACK`), the row is marked `published_at` and committed on its own.

`OUTBOX_PUBLISH_DELAY_MS` is an optional delay, in milliseconds, between the `PUBACK` and that mark. It defaults to `0` and exists for failure tests. If the API stops inside that window, a published event stays unmarked, so it is published again after the restart, and consumers drop the duplicate by `eventId`.
