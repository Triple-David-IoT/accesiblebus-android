package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.CommandIds

/**
 * Tracking (A15): the four facts of the request as separate blocks, Cancel with a confirmation,
 * Update, and a notice when the demo trip has finished. It scrolls, so it stays usable with
 * large text.
 */
@Composable
fun TrackingScreen(
    requestId: String,
    onBack: () -> Unit,
    onPreferences: () -> Unit = {},
    viewModel: PassengerViewModel = viewModel(factory = PassengerViewModel.Factory),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val commands by viewModel.commands.collectAsState()
    val live by viewModel.live.collectAsState()
    val stops by viewModel.stops.collectAsState()
    val request = snapshot.requests.find { it.id == requestId }
    var askCancel by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Seguimiento", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            if (live) "Avisos en vivo activos" else "Sin avisos en vivo: pulsa Actualizar para consultar el estado",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (request == null) {
            Text("Cargando la solicitud…", style = MaterialTheme.typography.bodyLarge)
        } else {
            val name = findStop(stops, request.physicalStopCode)?.name
            Text(name ?: "Parada ${request.physicalStopCode}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Código CRTM ${request.physicalStopCode} · parada ${request.sequence} del viaje de laboratorio · rampa para subir",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (tripFinished(snapshot, request)) {
                Text(
                    "El viaje de demostración ha finalizado; consulta el estado.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            trackingFacts(request).forEach { FactCard(it) }

            if (request.isOpen) {
                OutlinedButton(onClick = { askCancel = true }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar solicitud") }
            }
            // A refused cancel stays under the version it was sent with, so look for any of them.
            val prefix = CommandIds.request("cancel", request).substringBeforeLast(":")
            val cancel = commands.entries.lastOrNull { it.key.startsWith(prefix) }
            CommandStatus(cancel?.value, onRetry = { viewModel.cancel(request.id) }, onDismiss = { cancel?.let { viewModel.dismiss(it.key) } })

            if (askCancel && request.isOpen) {
                AlertDialog(
                    onDismissRequest = { askCancel = false },
                    title = { Text("¿Cancelar la solicitud?") },
                    text = { Text("El conductor dejará de tener tu solicitud de rampa en ${name ?: "la parada ${request.physicalStopCode}"}.") },
                    confirmButton = {
                        TextButton(onClick = {
                            askCancel = false
                            viewModel.cancel(request.id)
                        }) { Text("Sí, cancelar") }
                    },
                    dismissButton = { TextButton(onClick = { askCancel = false }) { Text("No") } },
                )
            }
        }

        OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") }
        OutlinedButton(onClick = onPreferences) { Text("Preferencias") }
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}

/** One fact; the mark and the words carry the meaning, not only the colour. */
@Composable
private fun FactCard(fact: Fact) {
    OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(12.dp)) {
            Text(if (fact.done) "✓" else "…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fact.title, style = MaterialTheme.typography.titleMedium)
                Text(fact.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
