
package dte.masteriot.accessiblebus.core.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appConfigDataStore by preferencesDataStore(
    name = "app_config"
)

class AppConfigRepository(
    private val context: Context
) {

    // -------------------------------------------------------------
    // General keys
    // -------------------------------------------------------------

    private val activeProfileKey =
        stringPreferencesKey("active_profile")

    // Shared accessibility preferences.
    private val voiceModeKey =
        stringPreferencesKey("voice_mode")

    private val vibrationEnabledKey =
        booleanPreferencesKey("vibration_enabled")

    private val highContrastEnabledKey =
        booleanPreferencesKey("high_contrast_enabled")

    private val languageKey =
        stringPreferencesKey("language")


    // -------------------------------------------------------------
    // Keys specific to each environment
    // -------------------------------------------------------------

    private fun keyName(
        profile: ConnectionProfile,
        name: String
    ): String {
        return "${profile.name.lowercase()}_$name"
    }

    private fun profileKey(profile: ConnectionProfile) =
        stringPreferencesKey(
            keyName(profile, "profile")
        )

    private fun apiUrlKey(profile: ConnectionProfile) =
        stringPreferencesKey(
            keyName(profile, "api_url")
        )

    private fun mqttHostKey(profile: ConnectionProfile) =
        stringPreferencesKey(
            keyName(profile, "mqtt_host")
        )

    private fun mqttPortKey(profile: ConnectionProfile) =
        intPreferencesKey(
            keyName(profile, "mqtt_port")
        )

    private fun tokenKey(profile: ConnectionProfile) =
        stringPreferencesKey(
            keyName(profile, "token")
        )


    // -------------------------------------------------------------
    // Read configuration
    // -------------------------------------------------------------

    fun config(
        profile: ConnectionProfile
    ): Flow<AppConfig> {

        val defaults = when (profile) {
            ConnectionProfile.LABORATORY ->
                AppConfigDefaults.laboratory

            ConnectionProfile.COURSE ->
                AppConfigDefaults.course
        }

        return context.appConfigDataStore.data.map { preferences ->

            AppConfig(
                profile = profile,

                // Environment-specific configuration.
                apiUrl = preferences[apiUrlKey(profile)]
                    ?: defaults.apiUrl,

                mqttHost = preferences[mqttHostKey(profile)]
                    ?: defaults.mqttHost,

                mqttPort = preferences[mqttPortKey(profile)]
                    ?: defaults.mqttPort,

                token = preferences[tokenKey(profile)]
                    ?: defaults.token,

                // General accessibility preferences.
                voiceMode = preferences[voiceModeKey]
                    ?.let { value ->
                        runCatching {
                            VoiceMode.valueOf(value)
                        }.getOrDefault(VoiceMode.AUTO)
                    }
                    ?: VoiceMode.AUTO,

                vibrationEnabled =
                    preferences[vibrationEnabledKey]
                        ?: true,

                highContrastEnabled =
                    preferences[highContrastEnabledKey]
                        ?: false
            )
        }
    }

    fun accessibilityPreferences(): Flow<AccessibilityPreferences> {
        return context.appConfigDataStore.data.map { preferences ->

            val voiceMode = preferences[voiceModeKey]
                ?.let { value ->
                    runCatching {
                        VoiceMode.valueOf(value)
                    }.getOrDefault(VoiceMode.AUTO)
                }
                ?: VoiceMode.AUTO

            AccessibilityPreferences(
                voiceMode = voiceMode,
                vibrationEnabled =
                    preferences[vibrationEnabledKey] ?: true,
                highContrastEnabled =
                    preferences[highContrastEnabledKey] ?: false,
                language = preferences[languageKey]
                    ?.let { value ->
                        runCatching {
                            AppLanguage.valueOf(value)
                        }.getOrDefault(AppLanguage.ENGLISH)
                    }
                    ?: AppLanguage.ENGLISH
            )
        }
    }

    suspend fun getConfig(
        profile: ConnectionProfile
    ): AppConfig {
        return config(profile).first()
    }


    // -------------------------------------------------------------
    // Save configuration
    // -------------------------------------------------------------

    suspend fun saveConfig(
        config: AppConfig
    ) {
        val profile = config.profile

        context.appConfigDataStore.edit { preferences ->

            // Save the environment.
            preferences[profileKey(profile)] = profile.name

            // Save only the connection parameters.
            preferences[apiUrlKey(profile)] = config.apiUrl

            preferences[mqttHostKey(profile)] =
                config.mqttHost

            preferences[mqttPortKey(profile)] =
                config.mqttPort

            preferences[tokenKey(profile)] =
                config.token
        }
    }

    suspend fun saveAccessibilityPreferences(
        preferences: AccessibilityPreferences
    ) {
        context.appConfigDataStore.edit { dataStore ->

            dataStore[voiceModeKey] =
                preferences.voiceMode.name

            dataStore[vibrationEnabledKey] =
                preferences.vibrationEnabled

            dataStore[highContrastEnabledKey] =
                preferences.highContrastEnabled

            dataStore[languageKey] =
                preferences.language.name
        }
    }

    // -------------------------------------------------------------
    // Active environment
    // -------------------------------------------------------------

    fun activeProfile(): Flow<ConnectionProfile> {

        return context.appConfigDataStore.data.map { preferences ->

            val profileName =
                preferences[activeProfileKey]

            profileName
                ?.let { name ->
                    runCatching {
                        ConnectionProfile.valueOf(name)
                    }.getOrNull()
                }
                ?: ConnectionProfile.LABORATORY
        }
    }


    suspend fun saveActiveProfile(
        profile: ConnectionProfile
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[activeProfileKey] = profile.name
        }
    }


}
