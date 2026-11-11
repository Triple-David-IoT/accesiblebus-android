package dte.masteriot.accessiblebus.ui.passenger

import dte.masteriot.accessiblebus.core.data.Stop
import dte.masteriot.accessiblebus.core.data.StopsState
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.StopVisit
import dte.masteriot.accessiblebus.core.net.TripState

/** Whether the passenger can ask for assistance at a stop of the laboratory trip, and why not. */
sealed interface VisitStatus {
    data object NoVisit : VisitStatus

    data class Ended(val visit: StopVisit) : VisitStatus

    data class Passed(val visit: StopVisit) : VisitStatus

    data class Open(val visit: StopVisit, val request: AssistanceRequest) : VisitStatus

    data class Available(val visit: StopVisit) : VisitStatus
}

fun visitStatus(snapshot: Snapshot, stopCode: String): VisitStatus {
    val trip = snapshot.trip
    val visit = snapshot.stopVisits.find { it.physicalStopCode == stopCode && it.tripId == trip?.id }
    return when {
        trip == null || visit == null -> VisitStatus.NoVisit
        trip.state == TripState.ENDED -> VisitStatus.Ended(visit)
        else -> snapshot.requests.find { it.stopVisitId == visit.id && it.isOpen }?.let { VisitStatus.Open(visit, it) }
            ?: if (visit.sequence <= trip.lastPassedSequence) VisitStatus.Passed(visit) else VisitStatus.Available(visit)
    }
}

fun statusText(status: VisitStatus): String = when (status) {
    VisitStatus.NoVisit -> "No hay viaje de laboratorio activo. Pulsa Actualizar."
    is VisitStatus.Ended -> "El viaje de demostración ha finalizado."
    is VisitStatus.Passed -> "El autobús ya pasó por esta parada."
    is VisitStatus.Open -> "Ya tienes una solicitud en esta parada: ${stateLabel(status.request.state)}."
    is VisitStatus.Available -> "Puedes pedir asistencia en esta parada."
}

/** The stop with this CRTM code, from the downloaded list or its local copy. */
fun findStop(state: StopsState, code: String): Stop? = (state as? StopsState.Ready)?.copy?.stops?.find { it.code == code }
