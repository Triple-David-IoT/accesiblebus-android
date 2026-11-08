package dte.masteriot.accessiblebus.ui.screens.hmi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dte.masteriot.accessiblebus.hmi.HmiViewModel
import dte.masteriot.accessiblebus.core.net.AssistanceRequest

@Composable
fun HmiScreen(viewModel: HmiViewModel = viewModel()) {
    val ui by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("LABORATORIO", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("Vehículo ${ui.vehicleId}", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(ui.tripLabel, style = MaterialTheme.typography.titleMedium)
            }
            item { StatusStrip(ui.rampText) }
            item { StatusStrip(ui.connectionText) }
            ui.error?.let { msg -> item { StatusStrip(msg) } }

            item { SectionTitle("Pendientes (${ui.pending.size})") }
            if (ui.pending.isEmpty()) item { Text("Sin solicitudes pendientes") }
            items(ui.pending, key = { it.id }) { RequestRow(it) }

            item { SectionTitle("En curso (${ui.inProgress.size})") }
            if (ui.inProgress.isEmpty()) item { Text("Sin asistencias en curso") }
            items(ui.inProgress, key = { it.id }) { RequestRow(it) }
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
private fun RequestRow(r: AssistanceRequest) =
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Parada ${r.physicalStopCode} · visita ${r.sequence}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (r.received) "Recibida por el terminal" else "Aún no confirmada por el terminal")
            Text("Estado: ${r.state}")
        }
    }