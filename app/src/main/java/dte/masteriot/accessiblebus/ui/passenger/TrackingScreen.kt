package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.accessiblebus.core.data.CommandIds

/**
 * Tracking screen (A15).
 *
 * Displays the four request facts as separate blocks, provides
 * cancellation with confirmation, an update action, and a notice
 * when the demo trip has finished.
 *
 * The screen is scrollable and remains usable with large text.
 */
@Composable
fun TrackingScreen(
    requestId: String,
    onBack: () -> Unit,
    onPreferences: () -> Unit = {},
    viewModel: PassengerViewModel = viewModel(
        factory = PassengerViewModel.Factory
    ),
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val commands by viewModel.commands.collectAsState()
    val live by viewModel.live.collectAsState()
    val stops by viewModel.stops.collectAsState()

    val request = snapshot.requests.find {
        it.id == requestId
    }

    var askCancel by rememberSaveable {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Tracking",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        heading()
                    }
            )

            IconButton(
                onClick = onPreferences,
                modifier = Modifier.sizeIn(
                    minWidth = 64.dp,
                    minHeight = 64.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open preferences",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            if (live) {
                "Live notifications are active"
            } else {
                "No live notifications: press Refresh to check the status"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (request == null) {
            Text(
                "Loading request…",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            val name = findStop(
                stops,
                request.physicalStopCode
            )?.name

            Text(
                name ?: "Stop ${request.physicalStopCode}",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "CRTM code ${request.physicalStopCode} · " +
                        "stop ${request.sequence} of the laboratory trip · " +
                        "boarding ramp",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (tripFinished(snapshot, request)) {
                Text(
                    "The demonstration trip has finished; check the status.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }

            trackingFacts(request).forEach {
                FactCard(it)
            }

            if (request.isOpen) {
                OutlinedButton(
                    onClick = {
                        askCancel = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel request")
                }
            }

            // A refused cancellation remains under the version
            // it was sent with, so look for any of its versions.
            val prefix = CommandIds.request(
                "cancel",
                request
            ).substringBeforeLast(":")

            val cancel = commands.entries.lastOrNull {
                it.key.startsWith(prefix)
            }

            CommandStatus(
                cancel?.value,
                onRetry = {
                    viewModel.cancel(request.id)
                },
                onDismiss = {
                    cancel?.let {
                        viewModel.dismiss(it.key)
                    }
                }
            )

            if (askCancel && request.isOpen) {
                AlertDialog(
                    onDismissRequest = {
                        askCancel = false
                    },
                    title = {
                        Text("Cancel the request?")
                    },
                    text = {
                        Text(
                            "The driver will no longer have your ramp " +
                                    "request at ${name ?: "stop ${request.physicalStopCode}"}."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                askCancel = false
                                viewModel.cancel(request.id)
                            }
                        ) {
                            Text("Yes, cancel")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                askCancel = false
                            }
                        ) {
                            Text("No")
                        }
                    },
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::refresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Displays one tracking fact.
 * Both the symbol and the text communicate its meaning,
 * rather than relying on colour alone.
 */
@Composable
private fun FactCard(fact: Fact) {
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Row(
            Modifier.padding(12.dp)
        ) {
            Text(
                if (fact.done) "✓" else "…",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(32.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    fact.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    fact.text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
