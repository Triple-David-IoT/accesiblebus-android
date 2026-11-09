package dte.masteriot.accessiblebus.ui.screens.hmi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dte.masteriot.accessiblebus.hmi.HmiViewModel
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dte.masteriot.accessiblebus.core.net.RejectReason

@Composable
fun HmiScreen(
    viewModel: HmiViewModel = viewModel(),
    onPreferences: () -> Unit
) {
    val ui by viewModel.state.collectAsState()

    var rejectingId by remember { mutableStateOf<String?>(null) }
    rejectingId?.let { id ->
        AlertDialog(
            onDismissRequest = { rejectingId = null },
            title = { Text("Reject request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RejectReason.entries.forEach { reason ->
                        OutlinedButton(
                            onClick = { viewModel.reject(id, reason); rejectingId = null },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        ) { Text(reasonLabel(reason)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { rejectingId = null }) { Text("Cancel") } },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        Text(
                            "LAB",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Vehicle ${ui.vehicleId}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            ui.tripLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    IconButton(
                        onClick = onPreferences,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Abrir preferencias",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item { StatusStrip(ui.rampText) }
            item { StatusStrip(ui.connectionText) }
            item {
                Button(
                    onClick = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Refresh") }
            }
            ui.error?.let { msg -> item { StatusStrip(msg) } }

            item { SectionTitle("Pending (${ui.pending.size})") }
            if (ui.pending.isEmpty()) item { Text("No pending requests", style = MaterialTheme.typography.bodyMedium) }
            items(ui.pending, key = { it.id }) { request ->
                RequestRow(request) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.accept(request.id) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        ) { Text("Accept") }
                        OutlinedButton(
                            onClick = { rejectingId = request.id },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        ) { Text("Reject") }
                    }
                }
            }

            item { SectionTitle("In Progress (${ui.inProgress.size})") }
            if (ui.inProgress.isEmpty()) item { Text("No ongoing assistance", style = MaterialTheme.typography.bodyMedium) }
            items(ui.inProgress, key = { it.id }) { request ->
                RequestRow(request) {
                    Button(
                        onClick = { viewModel.complete(request.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) { Text("Complete") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun StatusStrip(text: String) =
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
    }

@Composable
private fun RequestRow(r: AssistanceRequest, actions: @Composable () -> Unit = {}) =
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Stop ${r.physicalStopCode} · visit ${r.sequence}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (r.received) "Received by terminal" else "Not yet confirmed by terminal",
                style = MaterialTheme.typography.bodyMedium)
            Text("Status: ${r.state}", style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }

private fun reasonLabel(reason: RejectReason): String = when (reason) {
    RejectReason.RAMP_UNAVAILABLE -> "Ramp unavailable"
    RejectReason.UNSAFE_STOP -> "Unsafe stop"
    RejectReason.OTHER -> "Other"
}