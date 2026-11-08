package dte.masteriot.accessiblebus.core.config

data class AccessibilityPreferences(
    val voiceMode: VoiceMode = VoiceMode.AUTO,
    val vibrationEnabled: Boolean = true,
    val highContrastEnabled: Boolean = false
)