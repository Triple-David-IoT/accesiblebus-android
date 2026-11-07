package dte.masteriot.accessiblebus.core.config

import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient

enum class ConnectionProfile {
    LABORATORY,
    COURSE
}

enum class VoiceMode{
    AUTO,
    AlWAYS,
    OFF
}

data class AppConfig (
    val profile: ConnectionProfile,
    val apiUrl: String,
    val mqttHost: String,
    val mqttPort: Int,
    val token: String,
    val clientId: String,
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val vibrationEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false
)

//TODO: Add Real Connection data
object AppConfigDefaults {

    val laboratory = AppConfig(
        profile = ConnectionProfile.LABORATORY,
        apiUrl = "",
        mqttHost = "",
        mqttPort = 1883,
        token = "",
        clientId = "accessiblebus-android-lab"
    )

    val course = AppConfig(
        profile = ConnectionProfile.COURSE,
        apiUrl = "",
        mqttHost = "",
        mqttPort = 1883,
        token = "",
        clientId = "accessiblebus-android-course"
    )
}