package dte.masteriot.accessiblebus.core.config

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

//TODO: Add Real Connection data
object AppConfigDefaults {

    val laboratory = AppConfig(
        profile = ConnectionProfile.LABORATORY,
        apiUrl = "",
        mqttHost = "",
        mqttPort = 1883,
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