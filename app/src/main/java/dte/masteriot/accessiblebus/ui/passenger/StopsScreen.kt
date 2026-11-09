package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.Stop
import dte.masteriot.accessiblebus.core.data.StopsState
import dte.masteriot.accessiblebus.core.net.Snapshot
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The four stops of the demo line, with their CRTM code and name, in the order of the line.
 * Without network it shows the saved copy and says so. A stop opens Confirm for its visit in
 * the laboratory trip. The map (A11) and the stop detail (A12) come next.
 */
@Composable
fun StopsScreen(
    onStop: (String) -> Unit,
    onPreferences: () -> Unit = {},
    viewModel: PassengerViewModel = viewModel(factory = PassengerViewModel.Factory),
) {
    val stops by viewModel.stops.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadStops() }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Paradas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Línea 27 · Plaza de Castilla → Glorieta de Embajadores", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.loadStops(); viewModel.refresh() }) { Text("Actualizar") }
            OutlinedButton(onClick = onPreferences) { Text("Preferencias") }
        }

        when (val state = stops) {
            StopsState.Loading -> Text("Cargando paradas…")
            is StopsState.Failed -> {
                Text(
                    "No se pudieron cargar las paradas y no hay copia local. Comprueba la conexión y pulsa Actualizar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text("Detalle: ${state.problem}", style = MaterialTheme.typography.bodySmall)
            }
            is StopsState.Ready -> {
                if (state.fromCache) {
                    Text(
                        "Copia local del ${localTime(state.copy.fetchedAt)}: sin conexión con el CRTM.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                LazyColumn {
                    items(state.copy.stops, key = { it.code }) { stop ->
                        StopRow(stop, snapshot, onStop)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(stop: Stop, snapshot: Snapshot, onStop: (String) -> Unit) {
    val visit = snapshot.stopVisits.find { it.physicalStopCode == stop.code }
    val passed = visit != null && visit.sequence <= (snapshot.trip?.lastPassedSequence ?: 0)
    val open = visit != null && snapshot.requests.any { it.stopVisitId == visit.id && it.isOpen }
    val row = Modifier.fillMaxWidth()
    Column(
        (if (visit != null) row.clickable(role = Role.Button) { onStop(visit.id) } else row).padding(vertical = 16.dp),
    ) {
        Text(stop.name, style = MaterialTheme.typography.titleLarge)
        Text("Parada ${stop.code}", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                visit == null -> "Sin visita en el viaje de laboratorio"
                passed -> "El autobús ya pasó"
                open -> "Tienes una solicitud abierta"
                else -> "Visita ${visit.sequence} del viaje de laboratorio"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "2026-11-08T12:00:00Z" as local "08/11/2026 13:00". */
private fun localTime(iso: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)!!
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(parsed)
}.getOrDefault(iso)
