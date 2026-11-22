
package dte.masteriot.accessiblebus.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
import dte.masteriot.accessiblebus.core.config.VoiceMode

/**
 * Título de una sección de Preferencias.
 */
@Composable
fun PreferencesSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Selector accesible de modos de voz.
 *
 * Role.RadioButton permite que TalkBack identifique
 * cada opción y su estado seleccionado.
 */
@Composable
fun VoiceModeSelector(
    selectedMode: VoiceMode,
    onModeSelected: (VoiceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        VoiceMode.values().forEachIndexed { index, mode ->
            val selected = selectedMode == mode

            val label = when (mode) {
                VoiceMode.AUTO -> "AUTO"
                VoiceMode.ALWAYS -> "ALWAYS"
                VoiceMode.OFF -> "OFF"
            }

            val shape = when (index) {
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
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onModeSelected(mode) },
                        role = Role.RadioButton
                    ),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = shape,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 8.dp
                    ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Text(
                            text = "✓",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.width(6.dp))
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
}

/**
 * Descripción del modo de voz seleccionado.
 */
@Composable
fun VoiceModeDescription(
    mode: VoiceMode,
    modifier: Modifier = Modifier
) {
    val description = when (mode) {
        VoiceMode.AUTO ->
            "AUTO: habla si el teléfono está tapado (bolsillo o bolso). " +
                    "Si no, texto y vibración."

        VoiceMode.ALWAYS ->
            "ALWAYS: los avisos se reproducen siempre por voz."

        VoiceMode.OFF ->
            "OFF: los avisos se muestran mediante texto y vibración."
    }

    Text(
        text = description,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Fila accesible para un ajuste de tipo interruptor.
 */
@Composable
fun PreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

/**
 * Selector accesible del perfil de conexión.
 */
@Composable
fun ConnectionProfileSelector(
    selectedProfile: ConnectionProfile,
    onProfileSelected: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val profiles = listOf(
            ConnectionProfile.LABORATORY,
            ConnectionProfile.COURSE
        )

        profiles.forEachIndexed { index, profile ->
            val selected = selectedProfile == profile

            val label = when (profile) {
                ConnectionProfile.LABORATORY -> "Laboratorio"
                ConnectionProfile.COURSE -> "Curso"
            }

            val shape = when (index) {
                0 -> RoundedCornerShape(
                    topStart = 24.dp,
                    bottomStart = 24.dp
                )

                else -> RoundedCornerShape(
                    topEnd = 24.dp,
                    bottomEnd = 24.dp
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onProfileSelected(profile) },
                        role = Role.RadioButton
                    ),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = shape,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 8.dp
                    ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
}

/**
 * Campo de texto reutilizable para la configuración.
 */
@Composable
fun ConnectionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        }
    )
}

/**
 * Campo de token con opción de mostrar u ocultar su contenido.
 */
@Composable
fun TokenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Token") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(
                onClick = { onVisibilityChange(!visible) },
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Text(
                    text = if (visible) "Ocultar" else "Mostrar"
                )
            }
        }
    )
}

/**
 * Botón de probar voz.
 */
@Composable
fun TestVoiceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text("🔊  Probar voz")
    }
}

/**
 * Botones de guardar y volver.
 */
@Composable
fun PreferencesActionButtons(
    onSave: () -> Unit,
    onBack: () -> Unit,
    saveEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            enabled = saveEnabled
        ) {
            Text("Guardar preferencias")
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.outlinedButtonColors(),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Volver")
        }
    }
}

@Composable
fun ConnectionRestartNotice(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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

