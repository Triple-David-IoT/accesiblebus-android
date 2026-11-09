package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.CommandIds
import dte.masteriot.accessiblebus.core.net.TripState

/** Minimal Tracking for the first circuit: state, Update and Cancel. A15 completes it. */
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
    val request = snapshot.requests.find { it.id == requestId }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Seguimiento", style = MaterialTheme.typography.headlineMedium)
        Text(if (live) "Avisos en vivo activos" else "Sin avisos en vivo: pulsa Actualizar", style = MaterialTheme.typography.bodySmall)
        if (request == null) {
            Text("Cargando la solicitud…")
        } else {
            Text("Parada ${request.physicalStopCode}, visita ${request.sequence}", style = MaterialTheme.typography.titleLarge)
            Text(
                stateLabel(request.state),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(if (request.received) "Recibida por el terminal del autobús" else "Aún no recibida por el terminal del autobús")
            closeLabel(request.closeReason)?.let { Text("Motivo: $it") }
            if (snapshot.trip?.id == request.tripId && snapshot.trip?.state == TripState.ENDED) {
                Text("Viaje de demostración finalizado.")
            }

            if (request.isOpen) {
                OutlinedButton(onClick = { viewModel.cancel(request.id) }) { Text("Cancelar solicitud") }
            }
            // A refused cancel stays under the version it was sent with, so look for any of them.
            val cancel = commands.entries.lastOrNull { it.key.startsWith(CommandIds.request("cancel", request).substringBeforeLast(":")) }
            CommandStatus(cancel?.value, onRetry = { viewModel.cancel(request.id) }, onDismiss = { cancel?.let { viewModel.dismiss(it.key) } })
        }
        OutlinedButton(onClick = viewModel::refresh) { Text("Actualizar") }
        OutlinedButton(onClick = onPreferences) { Text("Preferencias") }
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}
