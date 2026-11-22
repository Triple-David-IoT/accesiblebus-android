
package dte.masteriot.accessiblebus.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.data.AppConfigRepository
import dte.masteriot.accessiblebus.data.Repository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PreferencesScreen(
    profile: ConnectionProfile,
    repository: AppConfigRepository,
    mqttRepository: Repository,
    onBack: () -> Unit,
    onTestVoice: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val preferencesViewModel: PreferencesViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return PreferencesViewModel(repository) as T
            }
        }
    )

    val preferencesUiState by preferencesViewModel.uiState
        .collectAsState()

    val accessibilityPreferences =
        preferencesUiState.accessibilityPreferences

    val voiceMode = accessibilityPreferences.voiceMode
    val vibrationEnabled = accessibilityPreferences.vibrationEnabled
    val highContrastEnabled = accessibilityPreferences.highContrastEnabled

    // Entorno que se está editando.
    var selectedProfile by remember(profile) {
        mutableStateOf(profile)
    }

    // Entorno que estaba activo al entrar en Preferences.
    val initialProfile = remember(profile) {
        profile
    }

    val configFlow = remember(selectedProfile) {
        repository.config(selectedProfile)
    }

    val config by configFlow.collectAsState(
        initial = when (selectedProfile) {
            ConnectionProfile.LABORATORY ->
                AppConfigDefaults.laboratory

            ConnectionProfile.COURSE ->
                AppConfigDefaults.course
        }
    )

    // Campos de conexión.
    var apiUrl by remember {
        mutableStateOf("")
    }

    var mqttHost by remember {
        mutableStateOf("")
    }

    var mqttPort by remember {
        mutableStateOf("1883")
    }

    var token by remember {
        mutableStateOf("")
    }

    var tokenVisible by remember {
        mutableStateOf(false)
    }

    var portError by remember {
        mutableStateOf(false)
    }

    // Text-to-Speech.
    var ttsReady by remember {
        mutableStateOf(false)
    }

    var textToSpeech by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }

        textToSpeech = tts

        onDispose {
            tts.stop()
            tts.shutdown()

            textToSpeech = null
            ttsReady = false
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            textToSpeech?.language = Locale.ENGLISH
        }
    }

    // Actualizar los campos de conexión al cambiar de entorno.
    LaunchedEffect(config) {
        apiUrl = config.apiUrl
        mqttHost = config.mqttHost
        mqttPort = config.mqttPort.toString()
        token = config.token

        portError = false
        tokenVisible = false
    }

    val connectionChanged = selectedProfile != initialProfile

    val savePreferences: () -> Unit = {
        val port = mqttPort.toIntOrNull()

        if (port != null && port in 1..65535) {
            val updatedConfig = AppConfig(
                profile = selectedProfile,
                apiUrl = apiUrl,
                mqttHost = mqttHost,
                mqttPort = port,
                token = token,
                voiceMode = voiceMode,
                vibrationEnabled = vibrationEnabled,
                highContrastEnabled = highContrastEnabled
            )

            coroutineScope.launch {
                repository.saveConfig(updatedConfig)

                preferencesViewModel.saveAccessibilityPreferences()

                repository.saveActiveProfile(selectedProfile)

                onBack()
            }
        } else {
            portError = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 56.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Preferencias",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal
            )

            // ---------------------------------------------------------
            // AVISOS
            // ---------------------------------------------------------

            PreferencesSectionTitle(
                title = "Avisos"
            )

            Text(
                text = "Voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            VoiceModeSelector(
                selectedMode = voiceMode,
                onModeSelected = { mode ->
                    preferencesViewModel.updateVoiceMode(mode)
                }
            )

            VoiceModeDescription(
                mode = voiceMode
            )

            // Vibración.
            PreferenceSwitchRow(
                title = "Vibración",
                description = "Vibra cuando llega una decisión del conductor.",
                checked = vibrationEnabled,
                onCheckedChange = { enabled ->
                    preferencesViewModel.updateVibration(enabled)
                }
            )

            // Alto contraste.
            PreferenceSwitchRow(
                title = "Alto contraste",
                description = "Fondo negro, texto blanco y acciones en amarillo.",
                checked = highContrastEnabled,
                onCheckedChange = { enabled ->
                    preferencesViewModel.updateHighContrast(enabled)
                }
            )

            // Probar voz.
            TestVoiceButton(
                onClick = onTestVoice,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------------------------------------------------------
            // CONEXIÓN
            // ---------------------------------------------------------

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            PreferencesSectionTitle(
                title = "Conexión"
            )
            // Selector Laboratory / Course.
            ConnectionProfileSelector(
                selectedProfile = selectedProfile,
                onProfileSelected = { newProfile ->
                    selectedProfile = newProfile
                }
            )

            // URL API.
            ConnectionTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = "URL de la API",
                modifier = Modifier.fillMaxWidth()
            )

            // Host y puerto.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionTextField(
                    value = mqttHost,
                    onValueChange = { mqttHost = it },
                    label = "Host del broker",
                    modifier = Modifier.weight(1.5f)
                )

                ConnectionTextField(
                    value = mqttPort,
                    onValueChange = {
                        mqttPort = it

                        val port = it.toIntOrNull()

                        portError = it.isNotBlank() &&
                                (port == null || port !in 1..65535)
                    },
                    label = "Puerto",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    isError = portError,
                    supportingText = if (portError) "Puerto inválido" else null
                )
            }

            // Token oculto.
            TokenTextField(
                value = token,
                onValueChange = { token = it },
                visible = tokenVisible,
                onVisibilityChange = { tokenVisible = it }
            )

            // Aviso de reinicio.
            if (connectionChanged) {
                ConnectionRestartNotice()
            }

            // Acciones.
            PreferencesActionButtons(
                onSave = savePreferences,
                onBack = onBack,
                saveEnabled = !portError && preferencesUiState.isLoaded
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}
