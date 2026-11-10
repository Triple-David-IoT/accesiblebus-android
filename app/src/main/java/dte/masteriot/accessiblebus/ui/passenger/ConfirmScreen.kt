package dte.masteriot.accessiblebus.ui.passenger

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.CommandIds
import dte.masteriot.accessiblebus.core.data.CommandState

/**
 * Confirm: the stop, the trip and "Solicitar asistencia". One column in portrait and two panels
 * in landscape, with the normal font size and no scroll. The command runs in the ViewModel and
 * its key is saved before sending, so turning the phone while it is on its way never sends it
 * twice; when the answer arrives, Tracking opens once.
 */
@Composable
fun ConfirmScreen(
    stopVisitId: String,
    onTracking: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PassengerViewModel = viewModel(factory = PassengerViewModel.Factory),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val commands by viewModel.commands.collectAsState()
    val created by viewModel.created.collectAsState()
    val stops by viewModel.stops.collectAsState()

    LaunchedEffect(created) {
        created?.let {
            viewModel.createdShown()
            onTracking(it)
        }
    }

    val visit = snapshot.stopVisits.find { it.id == stopVisitId }
    val stop = visit?.let { findStop(stops, it.physicalStopCode) }
    val status = visit?.let { visitStatus(snapshot, it.physicalStopCode) } ?: VisitStatus.NoVisit
    val commandId = CommandIds.create(stopVisitId)
    val command = commands[commandId]
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val details: @Composable ColumnScope.() -> Unit = {
        Text("Confirmar asistencia", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stop?.name ?: "Parada ${visit?.physicalStopCode ?: "desconocida"}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Código CRTM ${visit?.physicalStopCode ?: "-"}" + (stop?.let { " · EMT ${it.emtStopCode}" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Línea 27, viaje de laboratorio, parada ${visit?.sequence ?: "-"} de ${snapshot.stopVisits.size}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Asistencia: rampa para subir al autobús", style = MaterialTheme.typography.bodyLarge)
    }

    val actions: @Composable ColumnScope.() -> Unit = {
        when (status) {
            is VisitStatus.Available -> Button(
                onClick = { viewModel.create(stopVisitId) },
                enabled = command != CommandState.Sending,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Solicitar asistencia") }
            is VisitStatus.Open -> {
                Text(statusText(status), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { onTracking(status.request.id) }, modifier = Modifier.fillMaxWidth()) { Text("Ver seguimiento") }
            }
            else -> Text(statusText(status), style = MaterialTheme.typography.bodyLarge)
        }
        CommandStatus(command, onRetry = { viewModel.create(stopVisitId) }, onDismiss = { viewModel.dismiss(commandId) })
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }

    val screen = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
    if (landscape) {
        Row(screen, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = details)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically), content = actions)
        }
    } else {
        Column(screen, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            details()
            actions()
        }
    }
}
