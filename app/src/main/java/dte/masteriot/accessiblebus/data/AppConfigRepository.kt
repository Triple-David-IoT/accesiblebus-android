
package dte.masteriot.accessiblebus.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.VoiceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appConfigDataStore by preferencesDataStore(
    name = "app_config"
)

class AppConfigRepository(
    private val context: Context
) {
    private fun keyName(
        profile: ConnectionProfile,
        name: String
    ): String {
        return "${profile.name.lowercase()}_$name" // e.g. laboratory_api_url
    }

    private fun profileKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "profile"))

    private fun apiUrlKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "api_url"))

    private fun mqttHostKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "mqtt_host"))

    private fun mqttPortKey(profile: ConnectionProfile) =
        intPreferencesKey(keyName(profile, "mqtt_port"))

    private fun tokenKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "token"))

    private fun clientIdKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "client_id"))

    private fun voiceModeKey(profile: ConnectionProfile) =
        stringPreferencesKey(keyName(profile, "voice_mode"))

    private fun vibrationEnabledKey(profile: ConnectionProfile) =
        booleanPreferencesKey(keyName(profile, "vibration_enabled"))

    private fun highContrastEnabledKey(profile: ConnectionProfile) =
        booleanPreferencesKey(keyName(profile, "high_contrast_enabled"))


    fun config(profile: ConnectionProfile): Flow<AppConfig> {

        val defaults = when (profile) {
            ConnectionProfile.LABORATORY -> AppConfigDefaults.laboratory
            ConnectionProfile.COURSE -> AppConfigDefaults.course
        }

        return context.appConfigDataStore.data.map { preferences ->

            AppConfig(
                profile = profile,

                apiUrl = preferences[apiUrlKey(profile)]
                    ?: defaults.apiUrl,

                mqttHost = preferences[mqttHostKey(profile)]
                    ?: defaults.mqttHost,

                mqttPort = preferences[mqttPortKey(profile)]
                    ?: defaults.mqttPort,

                token = preferences[tokenKey(profile)]
                    ?: defaults.token,

                clientId = preferences[clientIdKey(profile)]
                    ?: defaults.clientId,

                voiceMode = preferences[voiceModeKey(profile)]
                    ?.let { value ->
                        runCatching {
                            VoiceMode.valueOf(value)
                        }.getOrDefault(VoiceMode.AUTO)
                    }
                    ?: defaults.voiceMode,

                vibrationEnabled =
                    preferences[vibrationEnabledKey(profile)]
                        ?: defaults.vibrationEnabled,

                highContrastEnabled =
                    preferences[highContrastEnabledKey(profile)]
                        ?: defaults.highContrastEnabled
            )
        }
    }

    suspend fun saveConfig(config: AppConfig) {

        val profile = config.profile

        context.appConfigDataStore.edit { preferences ->

            preferences[profileKey(profile)] = profile.name

            preferences[apiUrlKey(profile)] = config.apiUrl
            preferences[mqttHostKey(profile)] = config.mqttHost
            preferences[mqttPortKey(profile)] = config.mqttPort
            preferences[tokenKey(profile)] = config.token
            preferences[clientIdKey(profile)] = config.clientId

            preferences[voiceModeKey(profile)] = config.voiceMode.name
            preferences[vibrationEnabledKey(profile)] =
                config.vibrationEnabled
            preferences[highContrastEnabledKey(profile)] =
                config.highContrastEnabled
        }
    }

    suspend fun setApiUrl(
        profile: ConnectionProfile,
        apiUrl: String
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[apiUrlKey(profile)] = apiUrl
        }
    }

    suspend fun setMqttHost(
        profile: ConnectionProfile,
        host: String
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[mqttHostKey(profile)] = host
        }
    }

    suspend fun setMqttPort(
        profile: ConnectionProfile,
        port: Int
    ) {
        require(port in 1..65535) {
            "MQTT port must be between 1 and 65535"
        }

        context.appConfigDataStore.edit { preferences ->
            preferences[mqttPortKey(profile)] = port
        }
    }

    suspend fun setToken(
        profile: ConnectionProfile,
        token: String
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[tokenKey(profile)] = token
        }
    }

    suspend fun setClientId(
        profile: ConnectionProfile,
        clientId: String
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[clientIdKey(profile)] = clientId
        }
    }

    suspend fun setVoiceMode(
        profile: ConnectionProfile,
        mode: VoiceMode
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[voiceModeKey(profile)] = mode.name
        }
    }

    suspend fun setVibrationEnabled(
        profile: ConnectionProfile,
        enabled: Boolean
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[vibrationEnabledKey(profile)] = enabled
        }
    }

    suspend fun setHighContrastEnabled(
        profile: ConnectionProfile,
        enabled: Boolean
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[highContrastEnabledKey(profile)] = enabled
        }
    }
}
