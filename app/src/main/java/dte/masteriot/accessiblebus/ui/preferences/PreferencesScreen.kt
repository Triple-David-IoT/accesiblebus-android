
package dte.masteriot.accessiblebus.ui.preferences

import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.AppConfigRepository
import kotlinx.coroutines.launch

@Composable
fun PreferencesScreen(
    profile: ConnectionProfile,
    repository: AppConfigRepository,
    onBack: () -> Unit,
    onTestVoice: () -> Unit
) {
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

    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val voiceMode = accessibilityPreferences.voiceMode
    val vibrationEnabled = accessibilityPreferences.vibrationEnabled
    val highContrastEnabled = accessibilityPreferences.highContrastEnabled

    // Environment being edited.
    var selectedProfile by remember(profile) {
        mutableStateOf(profile)
    }

    // Environment that was active when Preferences was opened.
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

    // Connection fields.
    var apiUrl by remember {
        mutableStateOf("")
    }

    var mqttHost by remember {
        mutableStateOf("")
    }

    var mqttPort by remember {
        mutableStateOf("1883")
    }

    var portError by remember {
        mutableStateOf(false)
    }

    // Update connection fields when the environment changes.
    LaunchedEffect(config) {
        apiUrl = config.apiUrl
        mqttHost = config.mqttHost
        mqttPort = config.mqttPort.toString()

        portError = false
    }

    // The connection is read when the app starts (AppContainer).
    val connectionChanged = selectedProfile != initialProfile ||
        apiUrl != config.apiUrl || mqttHost != config.mqttHost || mqttPort != config.mqttPort.toString()

    val savePreferences: () -> Unit = {
        val port = mqttPort.toIntOrNull()

        if (port != null && port in 1..65535) {
            val updatedConfig = AppConfig(
                profile = selectedProfile,
                apiUrl = apiUrl,
                mqttHost = mqttHost,
                mqttPort = port,
                token = config.token,
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
                text = "Preferences",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal
            )

            // ---------------------------------------------------------
            // NOTIFICATIONS
            // ---------------------------------------------------------

            PreferencesSectionTitle(
                title = "Notifications"
            )

            Text(
                text = "Voice",
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

            // Vibration.
            PreferenceSwitchRow(
                title = "Vibration",
                description = "Vibrates when a decision from the driver is received.",
                checked = vibrationEnabled,
                onCheckedChange = { enabled ->
                    preferencesViewModel.updateVibration(enabled)
                }
            )

            // High contrast.
            PreferenceSwitchRow(
                title = "High contrast",
                description = "Black background, white text and yellow actions.",
                checked = highContrastEnabled,
                onCheckedChange = { enabled ->
                    preferencesViewModel.updateHighContrast(enabled)
                }
            )

            // Test voice.
            TestVoiceButton(
                onClick = onTestVoice,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------------------------------------------------------
            // CONNECTION
            // ---------------------------------------------------------

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            PreferencesSectionTitle(
                title = "Connection"
            )

            // Laboratory / Course selector.
            ConnectionProfileSelector(
                selectedProfile = selectedProfile,
                onProfileSelected = { newProfile ->
                    selectedProfile = newProfile
                }
            )

            // API URL.
            ConnectionTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = "API URL",
                modifier = Modifier.fillMaxWidth()
            )

            // Host and port.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionTextField(
                    value = mqttHost,
                    onValueChange = { mqttHost = it },
                    label = "Broker host",
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
                    label = "Port",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    isError = portError,
                    supportingText = if (portError) "Invalid port" else null
                )
            }

            // Restart notice.
            if (connectionChanged) {
                ConnectionRestartNotice()
            }

            // Actions.
            PreferencesActionButtons(
                onSave = savePreferences,
                onBack = onBack,
                saveEnabled = !portError && preferencesUiState.isLoaded,
                landscape = landscape
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}
