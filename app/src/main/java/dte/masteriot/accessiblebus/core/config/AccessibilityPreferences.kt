package dte.masteriot.accessiblebus.core.config

enum class AppLanguage(
    val tag: String
) {
    ENGLISH("en"),
    SPANISH("es")
}

data class AccessibilityPreferences(
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val vibrationEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH
)
