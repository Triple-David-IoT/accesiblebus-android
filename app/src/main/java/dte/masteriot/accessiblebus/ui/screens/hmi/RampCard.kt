package dte.masteriot.accessiblebus.ui.screens.hmi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dte.masteriot.accessiblebus.hmi.RampAction
import dte.masteriot.accessiblebus.hmi.RampTone
import dte.masteriot.accessiblebus.hmi.RampView

/**
 * Physical ramp on the HMI: state, limit switches, an alarm when something is wrong, and the
 * maneuvers. It only draws [ramp]: the enabled buttons come from its flags, never from the text.
 */
@Composable
fun RampCard(ramp: RampView, onAction: (RampAction) -> Unit) =
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ramp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                Text(ramp.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    ramp.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (ramp.tone) {
                        RampTone.ALERT -> MaterialTheme.colorScheme.error
                        RampTone.MOVING -> MaterialTheme.colorScheme.primary
                        RampTone.NORMAL -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(ramp.detail, style = MaterialTheme.typography.bodyLarge)
            }
            ramp.alarm?.let { alarm ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        alarm,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(RampAction.DEPLOY) },
                    enabled = ramp.canDeploy,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Deploy ramp") }
                OutlinedButton(
                    onClick = { onAction(RampAction.STOW) },
                    enabled = ramp.canStow,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("Stow ramp") }
            }
            if (ramp.canStop) {
                Button(
                    onClick = { onAction(RampAction.STOP) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Stop ramp", fontWeight = FontWeight.Bold) }
            }
        }
    }
