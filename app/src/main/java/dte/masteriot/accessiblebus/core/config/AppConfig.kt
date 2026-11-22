package dte.masteriot.accessiblebus.core.config

import dte.masteriot.accessiblebus.BuildConfig

enum class ConnectionProfile {
    LABORATORY,
    COURSE
}

enum class VoiceMode{
    AUTO,
    ALWAYS,
    OFF
}

data class AppConfig (
    val profile: ConnectionProfile,
    val apiUrl: String,
    val mqttHost: String,
    val mqttPort: Int,
    val token: String,
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val vibrationEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false
)

/** Laboratory: the build defaults (the laptop hotspot); Course: empty until someone fills it in. */
object AppConfigDefaults {

    private val broker = BuildConfig.MQTT_BROKER.substringAfter("://")

    val laboratory = AppConfig(
        profile = ConnectionProfile.LABORATORY,
        apiUrl = BuildConfig.API_BASE_URL,
        mqttHost = broker.substringBefore(":"),
        mqttPort = broker.substringAfter(":", "1883").toIntOrNull() ?: 1883,
        token = "",
    )

    val course = AppConfig(
        profile = ConnectionProfile.COURSE,
        apiUrl = "",
        mqttHost = "",
        mqttPort = 1883,
        token = "",
    )
}

object MqttClientIds {
    const val PASSENGER = "app-passenger-1"
    const val DRIVER = "hmi-demo-01"
}