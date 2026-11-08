"""Background publisher for PostgreSQL's transactional outbox."""

import json
import logging
import os
import threading
from typing import Any

import paho.mqtt.client as mqtt
import psycopg
from psycopg.rows import dict_row

from .settings import Settings

logger = logging.getLogger(__name__)

_BATCH_SIZE = 20
_IDLE_SECONDS = 0.5
_RECONNECT_SECONDS = 2.0
_PUBLISH_TIMEOUT = 5.0


class OutboxPublisher:
    """Publishes pending outbox events from a dedicated worker thread."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._stop_event = threading.Event()
        self._thread = threading.Thread(
            target=self._run,
            name="outbox-publisher",
            daemon=True,
        )

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        self._thread.join(timeout=5)
        if self._thread.is_alive():
            logger.warning("outbox.worker.stop_timeout")

    def _on_connect(
            self,
            client: mqtt.Client,
            userdata: Any,
            flags: Any,
            reason_code: Any,
            properties: Any = None,
    ) -> None:
        if getattr(reason_code, "is_failure", False):
            logger.warning(
                "outbox.mqtt.connection_refused reason=%s",
                reason_code,
            )
        else:
            logger.info("outbox.mqtt.connected")

    def _on_disconnect(
            self,
            client: mqtt.Client,
            userdata: Any,
            disconnect_flags: Any,
            reason_code: Any,
            properties: Any = None,
    ) -> None:
        if not self._stop_event.is_set():
            logger.warning(
                "outbox.mqtt.disconnected reason=%s",
                reason_code,
            )

    def _new_mqtt_client(self) -> mqtt.Client:
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id="api-outbox",
            protocol=mqtt.MQTTv311,
        )
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        return client

    def _connect_database(self) -> psycopg.Connection:
        return psycopg.connect(
            self._settings.database_url,
            row_factory=dict_row,
            autocommit=False,
        )

    def _run(self) -> None:
        client = self._new_mqtt_client()
        connection: psycopg.Connection | None = None

        try:
            client.connect_async(
                self._settings.mqtt_host,
                self._settings.mqtt_port,
                keepalive=30,
            )
            client.loop_start()

            while not self._stop_event.is_set():
                # Reconnect PostgreSQL independently from MQTT.
                if connection is None or connection.closed:
                    try:
                        connection = self._connect_database()
                        logger.info("outbox.db.connected")
                    except psycopg.OperationalError:
                        logger.exception(
                            "outbox.db.connection_failed retryable=true"
                        )
                        self._stop_event.wait(_RECONNECT_SECONDS)
                        continue
                    except psycopg.Error:
                        logger.exception(
                            "outbox.db.connection_error retryable=false"
                        )
                        self._stop_event.wait(_RECONNECT_SECONDS)
                        continue

                # Paho's network loop manages MQTT reconnection.
                if not client.is_connected():
                    self._stop_event.wait(_IDLE_SECONDS)
                    continue

                try:
                    self._publish_batch(connection, client)

                except psycopg.OperationalError:
                    logger.exception(
                        "outbox.db.connection_lost retryable=true"
                    )
                    self._rollback_quietly(connection)
                    self._close_database(connection)
                    connection = None
                    self._stop_event.wait(_RECONNECT_SECONDS)

                except psycopg.Error:
                    logger.exception(
                        "outbox.db.query_failed retryable=true"
                    )
                    self._rollback_quietly(connection)
                    self._stop_event.wait(_RECONNECT_SECONDS)

                except (TimeoutError, RuntimeError, mqtt.MQTTException):
                    logger.exception(
                        "outbox.mqtt.publish_failed retryable=true"
                    )
                    self._rollback_quietly(connection)
                    self._stop_event.wait(_IDLE_SECONDS)

                except Exception:
                    # Unexpected errors are not mislabeled as connection failures.
                    logger.exception("outbox.worker.unexpected_error")
                    self._rollback_quietly(connection)
                    self._stop_event.wait(_RECONNECT_SECONDS)

        finally:
            if connection is not None:
                self._close_database(connection)

            try:
                client.disconnect()
                client.loop_stop()
            except Exception:
                logger.exception("outbox.mqtt.shutdown_failed")

    def _publish_batch(
            self,
            connection: psycopg.Connection,
            client: mqtt.Client,
    ) -> None:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT seq, event_id, topic, payload
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY seq
                LIMIT %s
                FOR UPDATE SKIP LOCKED
                """,
                (_BATCH_SIZE,),
            )
            rows = cursor.fetchall()

            if not rows:
                connection.commit()
                self._stop_event.wait(_IDLE_SECONDS)
                return

            for row in rows:
                if self._stop_event.is_set():
                    break

                event_id = str(row["event_id"])
                topic = row["topic"]

                logger.info(
                    "outbox.publish.started event_id=%s topic=%s",
                    event_id,
                    topic,
                )

                delay_ms = int(
                    os.getenv("OUTBOX_PUBLISH_DELAY_MS", "0")
                )
                if delay_ms > 0:
                    self._stop_event.wait(delay_ms / 1000)
                    if self._stop_event.is_set():
                        break

                payload = row["payload"]
                if not isinstance(payload, str):
                    payload = json.dumps(
                        payload,
                        separators=(",", ":"),
                    )

                info = client.publish(
                    topic,
                    payload=payload,
                    qos=1,
                    retain=False,
                )

                if info.rc != mqtt.MQTT_ERR_SUCCESS:
                    raise RuntimeError(
                        f"MQTT publish returned rc={info.rc}"
                    )

                info.wait_for_publish(timeout=_PUBLISH_TIMEOUT)

                if not info.is_published():
                    raise TimeoutError(
                        "Timed out waiting for MQTT PUBACK"
                    )

                cursor.execute(
                    """
                    UPDATE outbox_event
                    SET published_at = now()
                    WHERE seq = %s
                      AND published_at IS NULL
                    """,
                    (row["seq"],),
                )

                logger.info(
                    "outbox.publish.confirmed event_id=%s topic=%s qos=1",
                    event_id,
                    topic,
                )

        connection.commit()

    @staticmethod
    def _rollback_quietly(
            connection: psycopg.Connection,
    ) -> None:
        try:
            connection.rollback()
        except psycopg.Error:
            logger.exception("outbox.db.rollback_failed")

    @staticmethod
    def _close_database(
            connection: psycopg.Connection,
    ) -> None:
        try:
            connection.close()
        except psycopg.Error:
            logger.exception("outbox.db.close_failed")


def start_outbox(settings: Settings) -> OutboxPublisher:
    publisher = OutboxPublisher(settings)
    publisher.start()
    return publisher