
package dte.masteriot.accessiblebus.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import dte.masteriot.accessiblebus.R

/**
 * Displays the four stops of the demo line, with their CRTM codes and names,
 * in the order of the route.
 *
 * Without a network connection, the saved local copy is displayed
 * together with an offline notification.
 *
 * Selecting a stop opens its details. The [onStop] callback receives
 * the CRTM stop code.
 *
 * "View map" displays the same stops on a map.
 * Stop details are opened from this screen.
 */
@Composable
fun StopsScreen(
    onStop: (String) -> Unit,
    onPreferences: () -> Unit = {},
    viewModel: PassengerViewModel = viewModel(
        factory = PassengerViewModel.Factory
    ),
) {
    val stops by viewModel.stops.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    val configuration = LocalConfiguration.current

    val isLandscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val openPreferencesDescription =
        stringResource(R.string.open_preferences)

    var showMap by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        viewModel.loadStops()
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.stops),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        heading()
                    }
            )

            IconButton(
                onClick = onPreferences,
                modifier = Modifier.size(64.dp)
                    .semantics {
                        contentDescription = openPreferencesDescription
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            stringResource(R.string.route_line_27),
            style = MaterialTheme.typography.bodyLarge
        )

        if (isLandscape) {
            // Horizontal layout when the device is in landscape orientation.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadStops()
                        viewModel.refresh()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.refresh))
                }

                OutlinedButton(
                    onClick = {
                        showMap = !showMap
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(
                            if (showMap) R.string.view_list else R.string.view_map
                        )
                    )
                }
            }
        } else {
            // Vertical layout when the device is in portrait orientation.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadStops()
                        viewModel.refresh()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.refresh))
                }

                OutlinedButton(
                    onClick = {
                        showMap = !showMap
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        stringResource(
                            if (showMap) R.string.view_list else R.string.view_map
                        )
                    )
                }
            }
        }

        when (val state = stops) {
            StopsState.Loading -> {
                Text(stringResource(R.string.loading_stops))
            }

            is StopsState.Failed -> {
                Text(
                    stringResource(R.string.stops_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )

                Text(
                    stringResource(R.string.details_format, state.problem),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            is StopsState.Ready -> {
                if (state.fromCache) {
                    Text(
                        stringResource(
                            R.string.local_copy_format,
                            localTime(state.copy.fetchedAt)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                if (showMap) {
                    Text(
                        stringResource(R.string.map_accessibility_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    StopsMap(
                        state.copy.stops,
                        onStop,
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            state.copy.stops,
                            key = { it.code }
                        ) { stop ->
                            StopRow(stop, snapshot, onStop)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    stop: Stop,
    snapshot: Snapshot,
    onStop: (String) -> Unit
) {
    val visit = snapshot.stopVisits.find {
        it.physicalStopCode == stop.code
    }

    val passed = visit != null &&
            visit.sequence <= (snapshot.trip?.lastPassedSequence ?: 0)

    val open = visit != null && snapshot.requests.any {
        it.stopVisitId == visit.id && it.isOpen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                onStop(stop.code)
            }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 56.dp)
            .padding(vertical = 16.dp)
    ) {
        Text(
            stop.name,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            stringResource(R.string.stop_format, stop.code),
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            when {
                visit == null ->
                    stringResource(R.string.no_visit)

                passed ->
                    stringResource(R.string.bus_passed)

                open ->
                    stringResource(R.string.open_request)

                else ->
                    stringResource(R.string.visit_format, visit.sequence)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Converts a UTC timestamp such as "2026-11-08T12:00:00Z"
 * into local time, for example "08/11/2026 13:00".
 */
private fun localTime(iso: String): String = runCatching {
    val parsed = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(iso)!!

    SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale.getDefault()
    ).format(parsed)
}.getOrDefault(iso)
