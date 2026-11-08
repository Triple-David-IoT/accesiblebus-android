
package dte.masteriot.accessiblebus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dte.masteriot.accessiblebus.core.config.AccessibilityPreferences
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.VoiceMode
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
    // Claves generales
    // -------------------------------------------------------------

    private val activeProfileKey =
        stringPreferencesKey("active_profile")

    // Preferencias de accesibilidad compartidas.
    private val voiceModeKey =
        stringPreferencesKey("voice_mode")

    private val vibrationEnabledKey =
        booleanPreferencesKey("vibration_enabled")

    private val highContrastEnabledKey =
        booleanPreferencesKey("high_contrast_enabled")


    // -------------------------------------------------------------
    // Claves específicas de cada entorno
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
    // Obtener configuración
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

                // Configuración específica del entorno.
                apiUrl = preferences[apiUrlKey(profile)]
                    ?: defaults.apiUrl,

                mqttHost = preferences[mqttHostKey(profile)]
                    ?: defaults.mqttHost,

                mqttPort = preferences[mqttPortKey(profile)]
                    ?: defaults.mqttPort,

                token = preferences[tokenKey(profile)]
                    ?: defaults.token,

                // Preferencias generales de accesibilidad.
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
                    preferences[highContrastEnabledKey] ?: false
            )
        }
    }

    suspend fun getConfig(
        profile: ConnectionProfile
    ): AppConfig {
        return config(profile).first()
    }


    // -------------------------------------------------------------
    // Guardar configuración
    // -------------------------------------------------------------

    suspend fun saveConfig(
        config: AppConfig
    ) {
        val profile = config.profile

        context.appConfigDataStore.edit { preferences ->

            // Guardar el entorno.
            preferences[profileKey(profile)] = profile.name

            // Guardar únicamente los parámetros de conexión.
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
        }
    }

    // -------------------------------------------------------------
    // Modificar parámetros de conexión
    // -------------------------------------------------------------

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


    // -------------------------------------------------------------
    // Modificar preferencias de accesibilidad
    // -------------------------------------------------------------

    suspend fun setVoiceMode(
        profile: ConnectionProfile,
        mode: VoiceMode
    ) {
        // profile se conserva en la firma para no romper
        // las llamadas existentes.
        context.appConfigDataStore.edit { preferences ->
            preferences[voiceModeKey] = mode.name
        }
    }


    suspend fun setVibrationEnabled(
        profile: ConnectionProfile,
        enabled: Boolean
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[vibrationEnabledKey] = enabled
        }
    }


    suspend fun setHighContrastEnabled(
        profile: ConnectionProfile,
        enabled: Boolean
    ) {
        context.appConfigDataStore.edit { preferences ->
            preferences[highContrastEnabledKey] = enabled
        }
    }


    // -------------------------------------------------------------
    // Entorno activo
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
