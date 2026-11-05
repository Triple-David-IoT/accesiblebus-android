"""Outbox publisher thread. Placeholder until C03 replaces the body; see README.md."""

import logging

from .settings import Settings

logger = logging.getLogger(__name__)


class OutboxPublisher:
    def stop(self) -> None:
        logger.info("Outbox publisher placeholder stopped")


def start_outbox(settings: Settings) -> OutboxPublisher:
    logger.info(
        "Outbox publisher placeholder started for %s:%s",
        settings.mqtt_host,
        settings.mqtt_port,
    )
    return OutboxPublisher()
