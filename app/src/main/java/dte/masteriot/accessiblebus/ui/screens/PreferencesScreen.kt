
package dte.masteriot.accessiblebus.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.AppConfigDefaults
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.data.AppConfigRepository
import dte.masteriot.accessiblebus.data.Repository
import dte.masteriot.accessiblebus.core.config.AccessibilityPreferences
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PreferencesScreen(
    profile: ConnectionProfile,
    repository: AppConfigRepository,
    mqttRepository: Repository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    // Preferencias de accesibilidad.
    var voiceMode by remember {
        mutableStateOf(VoiceMode.AUTO)
    }

    var vibrationEnabled by remember {
        mutableStateOf(true)
    }

    var highContrastEnabled by remember {
        mutableStateOf(false)
    }

    val accessibilityPreferences by repository
        .accessibilityPreferences()
        .collectAsState(
            initial = AccessibilityPreferences()
        )

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

    LaunchedEffect(accessibilityPreferences) {
        voiceMode = accessibilityPreferences.voiceMode
        vibrationEnabled =
            accessibilityPreferences.vibrationEnabled
        highContrastEnabled =
            accessibilityPreferences.highContrastEnabled
    }

    val connectionChanged = selectedProfile != initialProfile

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            Text(
                text = "Avisos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Selector AUTO / ALWAYS / OFF.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                VoiceMode.values().forEachIndexed { index, mode ->
                    val selected = voiceMode == mode

                    val label = when (mode) {
                        VoiceMode.AUTO -> "AUTO"
                        VoiceMode.ALWAYS -> "ALWAYS"
                        VoiceMode.OFF -> "OFF"
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                voiceMode = mode
                            },
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 24.dp,
                                bottomStart = 24.dp
                            )

                            VoiceMode.values().lastIndex ->
                                RoundedCornerShape(
                                    topEnd = 24.dp,
                                    bottomEnd = 24.dp
                                )

                            else -> RoundedCornerShape(0.dp)
                        },
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                Spacer(
                                    modifier = Modifier.width(6.dp)
                                )
                            }

                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = when (voiceMode) {
                    VoiceMode.AUTO ->
                        "AUTO: habla si el teléfono está tapado (bolsillo o bolso). " +
                                "Si no, texto y vibración."

                    VoiceMode.ALWAYS ->
                        "ALWAYS: los avisos se reproducen siempre por voz."

                    VoiceMode.OFF ->
                        "OFF: los avisos se muestran mediante texto y vibración."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Vibración.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Vibración",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Vibra cuando llega una decisión del conductor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        vibrationEnabled = it
                    }
                )
            }

            // Alto contraste.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Alto contraste",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Fondo negro, texto blanco y acciones en amarillo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = highContrastEnabled,
                    onCheckedChange = {
                        highContrastEnabled = it
                    }
                )
            }

            // Probar voz.
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
                enabled = ttsReady,
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("🔊  Probar voz")
            }

            // ---------------------------------------------------------
            // CONEXIÓN
            // ---------------------------------------------------------

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Conexión",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            // Selector Laboratory / Course.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf(
                    ConnectionProfile.LABORATORY,
                    ConnectionProfile.COURSE
                ).forEachIndexed { index, connectionProfile ->
                    val selected =
                        selectedProfile == connectionProfile

                    val label = when (connectionProfile) {
                        ConnectionProfile.LABORATORY -> "Laboratorio"
                        ConnectionProfile.COURSE -> "Curso"
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                selectedProfile = connectionProfile
                            },
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 24.dp,
                                bottomStart = 24.dp
                            )

                            else -> RoundedCornerShape(
                                topEnd = 24.dp,
                                bottomEnd = 24.dp
                            )
                        },
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                Spacer(
                                    modifier = Modifier.width(6.dp)
                                )
                            }

                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // URL API.
            OutlinedTextField(
                value = apiUrl,
                onValueChange = {
                    apiUrl = it
                },
                label = {
                    Text("URL de la API")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Host y puerto.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = mqttHost,
                    onValueChange = {
                        mqttHost = it
                    },
                    label = {
                        Text("Host del broker")
                    },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = mqttPort,
                    onValueChange = {
                        mqttPort = it

                        val port = it.toIntOrNull()

                        portError = it.isNotBlank() &&
                                (port == null || port !in 1..65535)
                    },
                    label = {
                        Text("Puerto")
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    isError = portError,
                    supportingText = {
                        if (portError) {
                            Text(
                                text = "Puerto inválido"
                            )
                        }
                    }
                )
            }

            // Token oculto.
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                },
                label = {
                    Text("Token")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Text(
                        text = if (tokenVisible) "Ocultar" else "Mostrar",
                        modifier = Modifier
                            .clickable {
                                tokenVisible = !tokenVisible
                            }
                            .padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )

            // Aviso de reinicio.
            if (connectionChanged) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "↻  Reinicia la app para aplicar la conexión",
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Guardar.
            Button(
                onClick = {
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

                            repository.saveAccessibilityPreferences(
                                AccessibilityPreferences(
                                    voiceMode = voiceMode,
                                    vibrationEnabled = vibrationEnabled,
                                    highContrastEnabled = highContrastEnabled
                                )
                            )

                            repository.saveActiveProfile(
                                selectedProfile
                            )

                            onBack()
                        }
                    } else {
                        portError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !portError
            ) {
                Text("Guardar preferencias")
            }

            // Volver.
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Volver")
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}
