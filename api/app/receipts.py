"""Receipts subscriber thread: records HMI receipts and tells the passenger (B06, K2)."""

import json
import logging
from uuid import UUID, uuid4

import paho.mqtt.client as mqtt

from . import db
from .auth import TOKENS
from .settings import Settings

logger = logging.getLogger(__name__)

_TOPIC_FILTER = "terminals/+/receipts"
_SCHEMA_VERSION = 3

# K7: the schema does not link terminals to vehicles, so use the demo identities.
_TERMINAL_VEHICLE = {
    actor.terminal_id: actor.vehicle_id
    for actor in TOKENS.values()
    if actor.terminal_id and actor.vehicle_id
}


def record_receipt(topic: str, payload: bytes) -> bool:
    """Store one HMI receipt and queue request.receipt_recorded. True if a new receipt was stored."""
    try:
        data = json.loads(payload)
        schema_version = data["schemaVersion"]
        event_id = UUID(data["eventId"])
        request_id = UUID(data["requestId"])
        terminal_id = data["terminalId"]
    except (ValueError, KeyError, TypeError, AttributeError):
        logger.warning("receipts.invalid_message topic=%s", topic)
        return False

    parts = topic.split("/")
    if schema_version != _SCHEMA_VERSION or len(parts) != 3 or parts[1] != terminal_id:
        logger.warning("receipts.rejected topic=%s terminal=%s", topic, terminal_id)
        return False
    expected_vehicle = _TERMINAL_VEHICLE.get(terminal_id)
    if expected_vehicle is None:
        logger.warning("receipts.unknown_terminal terminal=%s", terminal_id)
        return False

    with db.transaction() as cursor:
        cursor.execute(
            """
            SELECT ar.id, ar.owner_subject, ar.version, t.id AS trip_id, t.vehicle_id
            FROM assistance_request ar
            JOIN stop_visit sv ON sv.id = ar.stop_visit_id
            JOIN trip t ON t.id = sv.trip_id
            WHERE ar.created_event_id = %s
            """,
            (event_id,),
        )
        request = cursor.fetchone()
        if request is None or request["id"] != request_id:
            logger.warning("receipts.unknown_request event_id=%s", event_id)
            return False
        if request["vehicle_id"] != expected_vehicle:
            logger.warning("receipts.wrong_vehicle terminal=%s event_id=%s", terminal_id, event_id)
            return False

        cursor.execute(
            """
            INSERT INTO terminal_receipt (created_event_id, request_id, terminal_id)
            VALUES (%s, %s, %s)
            ON CONFLICT DO NOTHING
            RETURNING created_event_id
            """,
            (event_id, request_id, terminal_id),
        )
        if cursor.fetchone() is None:  # duplicate receipt: no second event
            logger.info("receipts.duplicate event_id=%s terminal=%s", event_id, terminal_id)
            return False

        new_event_id = uuid4()
        db.enqueue_event(
            cursor,
            event_id=new_event_id,
            event_type="request.receipt_recorded",
            trip_id=request["trip_id"],
            topic=f"users/{request['owner_subject']}/events",
            payload={
                "schemaVersion": _SCHEMA_VERSION,
                "eventId": str(new_event_id),
                "eventType": "request.receipt_recorded",
                "requestId": str(request_id),
                "tripId": str(request["trip_id"]),
                "version": request["version"],
                "payload": {"received": True},
            },
            request_id=request_id,
        )
    logger.info("receipts.recorded event_id=%s terminal=%s", event_id, terminal_id)
    return True


class ReceiptsSubscriber:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id="api-receipts",
            protocol=mqtt.MQTTv311,
        )
        self._client.on_connect = self._on_connect
        self._client.on_message = self._on_message

    def start(self) -> None:
        self._client.connect_async(self._settings.mqtt_host, self._settings.mqtt_port, keepalive=30)
        self._client.loop_start()

    def stop(self) -> None:
        self._client.disconnect()
        self._client.loop_stop()

    def _on_connect(self, client, userdata, flags, reason_code, properties=None) -> None:
        if getattr(reason_code, "is_failure", False):
            logger.warning("receipts.mqtt.connection_refused reason=%s", reason_code)
            return
        client.subscribe(_TOPIC_FILTER, qos=1)  # clean session: subscribe on every connect
        logger.info("receipts.mqtt.subscribed topic=%s", _TOPIC_FILTER)

    def _on_message(self, client, userdata, msg) -> None:
        try:
            record_receipt(msg.topic, msg.payload)
        except Exception:
            # The HMI sends the receipt again on its next /sync (B11), so a lost one is recoverable.
            logger.exception("receipts.processing_failed topic=%s", msg.topic)


def start_receipts(settings: Settings) -> ReceiptsSubscriber:
    subscriber = ReceiptsSubscriber(settings)
    subscriber.start()
    return subscriber