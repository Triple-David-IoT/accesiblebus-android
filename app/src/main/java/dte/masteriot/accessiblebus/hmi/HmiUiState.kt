package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState

data class HmiUiState(
    val vehicleId: String = "—",
    val tripLabel: String = "Sin viaje",
    val pending: List<AssistanceRequest> = emptyList(),
    val inProgress: List<AssistanceRequest> = emptyList(),
    val rampText: String = "Rampa: sin datos",
    val connectionText: String = "Conexión: sin conectar (datos de ejemplo)",
    val error: String? = null
)

fun Snapshot.toHmiUiState(): HmiUiState {
    val current = trip
    val ofTrip = requests.filter { it.tripId == current?.id }
    return HmiUiState(
        vehicleId = current?.vehicleId ?: "—",
        tripLabel = when (current?.state) {
            TripState.ACTIVE -> "Viaje activo"
            TripState.ENDED -> "Viaje finalizado"
            null -> "Sin viaje"
        },
        pending = ofTrip.filter { it.state == RequestState.PENDING }.sortedBy { it.sequence },
        inProgress = ofTrip.filter { it.state == RequestState.ACCEPTED }.sortedBy { it.sequence }
    )
}