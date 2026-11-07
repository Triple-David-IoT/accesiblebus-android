package dte.masteriot.accessiblebus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dte.masteriot.accessiblebus.core.config.AppConfig
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

    private object Keys {
        val PROFILE = stringPreferencesKey("profile")
        val API_URL = stringPreferencesKey("api_url")
        val MQTT_HOST = stringPreferencesKey("mqtt_host")
        val MQTT_PORT = intPreferencesKey("mqtt_port")
        val TOKEN = stringPreferencesKey("token")
        val CLIENT_ID = stringPreferencesKey("client_id")

        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
    }

    // Produce a new configuration each time a value is changedç
    // TODO: Add real connections
    val config: Flow<AppConfig> =
        context.appConfigDataStore.data.map { preferences ->

            AppConfig(
                profile = preferences[Keys.PROFILE]
                    ?.let { value ->
                        runCatching {
                            ConnectionProfile.valueOf(value)
                        }.getOrDefault(ConnectionProfile.LABORATORY)
                    }
                    ?: ConnectionProfile.LABORATORY,

                apiUrl = preferences[Keys.API_URL] ?: "",

                mqttHost = preferences[Keys.MQTT_HOST] ?: "",

                mqttPort = preferences[Keys.MQTT_PORT] ?: 1883,

                token = preferences[Keys.TOKEN] ?: "",

                clientId = preferences[Keys.CLIENT_ID]
                    ?: "accessiblebus-android",

                voiceMode = preferences[Keys.VOICE_MODE]
                    ?.let { value ->
                        runCatching {
                            VoiceMode.valueOf(value)
                        }.getOrDefault(VoiceMode.AUTO)
                    }
                    ?: VoiceMode.AUTO,

                vibrationEnabled =
                    preferences[Keys.VIBRATION_ENABLED] ?: true,

                highContrastEnabled =
                    preferences[Keys.HIGH_CONTRAST_ENABLED] ?: false
            )
        }

    /**
     * Guarda una configuración completa.
     * Guarda una configuración completa.
     */
    suspend fun saveConfig(config: AppConfig) {
        context.appConfigDataStore.edit { preferences ->

            preferences[Keys.PROFILE] = config.profile.name
            preferences[Keys.API_URL] = config.apiUrl
            preferences[Keys.MQTT_HOST] = config.mqttHost
            preferences[Keys.MQTT_PORT] = config.mqttPort
            preferences[Keys.TOKEN] = config.token
            preferences[Keys.CLIENT_ID] = config.clientId

            preferences[Keys.VOICE_MODE] = config.voiceMode.name
            preferences[Keys.VIBRATION_ENABLED] = config.vibrationEnabled
            preferences[Keys.HIGH_CONTRAST_ENABLED] =
                config.highContrastEnabled
        }
    }

    suspend fun setProfile(profile: ConnectionProfile) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.PROFILE] = profile.name
        }
    }

    suspend fun setApiUrl(apiUrl: String) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.API_URL] = apiUrl
        }
    }

    suspend fun setMqttHost(host: String) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.MQTT_HOST] = host
        }
    }

    suspend fun setMqttPort(port: Int) {
        require(port in 1..65535) {
            "MQTT port must be between 1 and 65535"
        }

        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.MQTT_PORT] = port
        }
    }

    suspend fun setToken(token: String) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.TOKEN] = token
        }
    }

    suspend fun setClientId(clientId: String) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.CLIENT_ID] = clientId
        }
    }

    suspend fun setVoiceMode(mode: VoiceMode) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.VOICE_MODE] = mode.name
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setHighContrastEnabled(enabled: Boolean) {
        context.appConfigDataStore.edit { preferences ->
            preferences[Keys.HIGH_CONTRAST_ENABLED] = enabled
        }
    }
}