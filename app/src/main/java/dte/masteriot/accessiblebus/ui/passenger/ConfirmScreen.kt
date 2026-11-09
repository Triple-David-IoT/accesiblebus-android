package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.CommandIds
import dte.masteriot.accessiblebus.core.data.CommandState

/** Minimal Confirm for the first circuit. A12 adds the stop name and the landscape layout. */
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

    LaunchedEffect(created) {
        created?.let {
            viewModel.createdShown()
            onTracking(it)
        }
    }

    val visit = snapshot.stopVisits.find { it.id == stopVisitId }
    val open = snapshot.requests.find { it.stopVisitId == stopVisitId && it.isOpen }
    val passed = visit != null && visit.sequence <= (snapshot.trip?.lastPassedSequence ?: 0)
    val commandId = CommandIds.create(stopVisitId)
    val command = commands[commandId]

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Confirmar asistencia", style = MaterialTheme.typography.headlineMedium)
        Text("Parada ${visit?.physicalStopCode ?: "desconocida"}", style = MaterialTheme.typography.titleLarge)
        Text("Viaje de laboratorio, visita ${visit?.sequence ?: "-"}. Rampa para subir al autobús.")

        when {
            open != null -> {
                Text("Ya tienes una solicitud en esta parada: ${stateLabel(open.state)}.")
                Button(onClick = { onTracking(open.id) }, modifier = Modifier.fillMaxWidth()) { Text("Ver seguimiento") }
            }
            passed -> Text("El autobús ya pasó por esta parada.")
            else -> Button(
                onClick = { viewModel.create(stopVisitId) },
                enabled = visit != null && command != CommandState.Sending,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Solicitar asistencia") }
        }

        CommandStatus(command, onRetry = { viewModel.create(stopVisitId) }, onDismiss = { viewModel.dismiss(commandId) })
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}
