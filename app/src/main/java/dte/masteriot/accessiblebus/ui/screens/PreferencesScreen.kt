package dte.masteriot.accessiblebus.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.data.AppConfigRepository
import java.util.Locale

@Composable
fun PreferencesScreen(
    profile: ConnectionProfile,
    repository: AppConfigRepository,
    mqttRepository: dte.masteriot.accessiblebus.data.Repository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configFlow = remember(profile) {
        repository.config(profile)
    }

    val config by configFlow.collectAsState(
        initial = when (profile) {
            ConnectionProfile.LABORATORY -> AppConfigDefaults.laboratory
            ConnectionProfile.COURSE -> AppConfigDefaults.course
        }
    )

    var apiUrl by remember { mutableStateOf("") }
    var mqttHost by remember { mutableStateOf("") }
    var mqttPort by remember { mutableStateOf("1883") }
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }

    var voiceMode by remember { mutableStateOf(VoiceMode.AUTO) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var highContrastEnabled by remember { mutableStateOf(false) }

    var ttsReady by remember { mutableStateOf(false) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS

            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH
            }
        }

        textToSpeech = tts

        onDispose {
            tts.stop()
            tts.shutdown()
            textToSpeech = null
        }
    }

    LaunchedEffect(config) {
        apiUrl = config.apiUrl
        mqttHost = config.mqttHost
        mqttPort = config.mqttPort.toString()
        token = config.token
        clientId = config.clientId
        voiceMode = config.voiceMode
        vibrationEnabled = config.vibrationEnabled
        highContrastEnabled = config.highContrastEnabled
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = when (profile) {
                    ConnectionProfile.LABORATORY -> "Laboratory profile"
                    ConnectionProfile.COURSE -> "Course profile"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = mqttHost,
                onValueChange = { mqttHost = it },
                label = { Text("MQTT host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = mqttPort,
                onValueChange = { mqttPort = it },
                label = { Text("MQTT port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Authentication token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("MQTT client ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Voice announcements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            VoiceMode.values().forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = voiceMode == mode,
                        onClick = { voiceMode = mode }
                    )

                    Text(
                        text = when (mode) {
                            VoiceMode.AUTO -> "Automatic"
                            VoiceMode.ALWAYS -> "Always enabled"
                            VoiceMode.OFF -> "Disabled"
                        }
                    )
                }
            }

            Button(
                onClick = {
                    if (ttsReady) {
                        textToSpeech?.speak(
                            "Voice announcements are working.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "accessiblebus_test"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ttsReady
            ) {
                Text("Test voice")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vibration",
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        vibrationEnabled = it
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "High contrast",
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = highContrastEnabled,
                    onCheckedChange = {
                        highContrastEnabled = it
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val port = mqttPort.toIntOrNull() ?: 1883

                    val updatedConfig = AppConfig(
                        profile = profile,
                        apiUrl = apiUrl,
                        mqttHost = mqttHost,
                        mqttPort = port,
                        token = token,
                        clientId = clientId,
                        voiceMode = voiceMode,
                        vibrationEnabled = vibrationEnabled,
                        highContrastEnabled = highContrastEnabled
                    )

                    coroutineScope.launch {
                        // Save the configuration for the selected profile.
                        repository.saveConfig(updatedConfig)

                        // Get the profile currently being used by the app.
                        val activeProfile = repository.activeProfile().first()

                        // Reconnect only if the edited profile is the active one.
                        if (profile == activeProfile) {
                            mqttRepository.connectMqtt(activeProfile)
                        }

                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save preferences")
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}