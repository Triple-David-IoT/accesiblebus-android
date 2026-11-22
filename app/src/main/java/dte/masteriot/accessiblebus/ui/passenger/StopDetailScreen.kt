
package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Displays a stop's CRTM name and code, the EMT number shown
 * on the stop sign, and its visit status in the laboratory trip.
 *
 * Assistance can only be requested while the bus has not passed.
 */
@Composable
fun StopDetailScreen(
    stopCode: String,
    onConfirm: (String) -> Unit,
    onTracking: (String) -> Unit,
    onBack: () -> Unit,
    onPreferences: () -> Unit = {},
    viewModel: PassengerViewModel = viewModel(
        factory = PassengerViewModel.Factory
    ),
) {
    val stops by viewModel.stops.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    LaunchedEffect(Unit) {
        if (findStop(stops, stopCode) == null) {
            viewModel.loadStops()
        }
    }

    val stop = findStop(stops, stopCode)
    val status = visitStatus(snapshot, stopCode)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stop?.name ?: "Stop $stopCode",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics {
                    heading()
                }
            )

            Text(
                "CRTM code: $stopCode",
                style = MaterialTheme.typography.titleMedium
            )

            stop?.let {
                Text(
                    "EMT stop number: ${it.emtStopCode}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Text(
                "Line 27 · Plaza de Castilla → Glorieta de Embajadores",
                style = MaterialTheme.typography.bodyLarge
            )

            val visit = when (status) {
                is VisitStatus.Available -> status.visit
                is VisitStatus.Open -> status.visit
                is VisitStatus.Passed -> status.visit
                is VisitStatus.Ended -> status.visit
                VisitStatus.NoVisit -> null
            }

            visit?.let {
                Text(
                    "Laboratory trip: stop ${it.sequence} of ${snapshot.stopVisits.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                statusText(status),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        when (status) {
            is VisitStatus.Available -> {
                Button(
                    onClick = {
                        onConfirm(status.visit.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Request assistance here")
                }
            }

            is VisitStatus.Open -> {
                Button(
                    onClick = {
                        onTracking(status.request.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("View tracking")
                }
            }

            else -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text("Request assistance here")
                }
            }
        }

        OutlinedButton(
            onClick = viewModel::refresh,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Refresh")
        }

        OutlinedButton(
            onClick = onPreferences,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Preferences")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Text("Back")
        }
    }
}
