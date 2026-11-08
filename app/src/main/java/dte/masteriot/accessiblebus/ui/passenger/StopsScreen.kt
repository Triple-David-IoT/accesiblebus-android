package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Minimal list of the stops of the laboratory trip, enough to reach Confirm. A10 replaces it
 * with the CRTM stop names, the cache and the map.
 */
@Composable
fun StopsScreen(onStop: (String) -> Unit, viewModel: PassengerViewModel = viewModel(factory = PassengerViewModel.Factory)) {
    val snapshot by viewModel.snapshot.collectAsState()
    val passed = snapshot.trip?.lastPassedSequence ?: 0

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Paradas", style = MaterialTheme.typography.headlineMedium)
        Text("Viaje de laboratorio, línea 27", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(vertical = 8.dp)) { Text("Actualizar") }
        if (snapshot.trip == null) Text("Sin viaje activo. Pulsa Actualizar.")
        LazyColumn {
            items(snapshot.stopVisits, key = { it.id }) { visit ->
                val open = snapshot.requests.any { it.stopVisitId == visit.id && it.isOpen }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onStop(visit.id) }
                        .padding(vertical = 16.dp),
                ) {
                    Text("Parada ${visit.physicalStopCode}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            visit.sequence <= passed -> "Ya pasó"
                            open -> "Tienes una solicitud abierta"
                            else -> "Visita ${visit.sequence} del viaje"
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
