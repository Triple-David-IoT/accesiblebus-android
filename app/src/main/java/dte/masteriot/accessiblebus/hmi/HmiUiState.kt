package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState

data class HmiUiState(
    val vehicleId: String = "—",
    val tripLabel: String = "No trip",
    val pending: List<AssistanceRequest> = emptyList(),
    val inProgress: List<AssistanceRequest> = emptyList(),
    val rampText: String = "Ramp: no data",
    val connectionText: String = "Connection: disconnected (sample data)",
    val error: String? = null,
    val tripActive: Boolean = false,
    val canAdvance: Boolean = false
)

fun Snapshot.toHmiUiState(): HmiUiState {
    val current = trip
    val ofTrip = requests.filter { it.tripId == current?.id }
    return HmiUiState(
        vehicleId = current?.vehicleId ?: "—",
        tripLabel = when (current?.state) {
            TripState.ACTIVE -> "Active trip · " + nextStopText(current.lastPassedSequence)
            TripState.ENDED -> "Trip ended"
            null -> "No trip"
        },
        pending = ofTrip.filter { it.state == RequestState.PENDING }.sortedBy { it.sequence },
        inProgress = ofTrip.filter { it.state == RequestState.ACCEPTED }.sortedBy { it.sequence },
        tripActive = current?.state == TripState.ACTIVE,
        canAdvance = current?.state == TripState.ACTIVE && current.lastPassedSequence < stopVisits.size
    )
}

/** Where the bus goes next, so the driver sees each "Stop passed" land: "next stop 459 (2 of 4)". */
private fun Snapshot.nextStopText(lastPassed: Int): String {
    val visits = stopVisits.filter { it.tripId == trip?.id }.sortedBy { it.sequence }
    val next = visits.firstOrNull { it.sequence > lastPassed }
    return if (next == null) "all ${visits.size} stops passed"
    else "next stop ${next.physicalStopCode} (${next.sequence} of ${visits.size})"
}
