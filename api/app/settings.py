"""Configuration read from environment variables."""

import os

from pydantic import BaseModel, ConfigDict


class Settings(BaseModel):
    model_config = ConfigDict(frozen=True)

    database_url: str
    mqtt_host: str
    mqtt_port: int = 1883

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            database_url=os.environ["DATABASE_URL"],
            mqtt_host=os.environ["MQTT_HOST"],
            mqtt_port=os.environ.get("MQTT_PORT", 1883),
        )
