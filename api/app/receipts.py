"""Receipts subscriber thread. Placeholder until B06 replaces the body; see README.md."""

import logging

from .settings import Settings

logger = logging.getLogger(__name__)


class ReceiptsSubscriber:
    def stop(self) -> None:
        logger.info("Receipts subscriber placeholder stopped")


def start_receipts(settings: Settings) -> ReceiptsSubscriber:
    logger.info(
        "Receipts subscriber placeholder started for %s:%s",
        settings.mqtt_host,
        settings.mqtt_port,
    )
    return ReceiptsSubscriber()
