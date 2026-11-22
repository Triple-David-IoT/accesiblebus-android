
package dte.masteriot.accessiblebus.ui.passenger

import dte.masteriot.accessiblebus.core.data.Stop
import dte.masteriot.accessiblebus.core.data.StopsState
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.StopVisit
import dte.masteriot.accessiblebus.core.net.TripState

/**
 * Whether the passenger can ask for assistance at a stop of the laboratory trip, and why not.
 */
sealed interface VisitStatus {
    data object NoVisit : VisitStatus

    data class Ended(val visit: StopVisit) : VisitStatus

    data class Passed(val visit: StopVisit) : VisitStatus

    data class Open(val visit: StopVisit, val request: AssistanceRequest) : VisitStatus

    data class Available(val visit: StopVisit) : VisitStatus
}

fun visitStatus(snapshot: Snapshot, stopCode: String): VisitStatus {
    val trip = snapshot.trip
    val visit = snapshot.stopVisits.find {
        it.physicalStopCode == stopCode && it.tripId == trip?.id
    }

    return when {
        trip == null || visit == null -> VisitStatus.NoVisit
        trip.state == TripState.ENDED -> VisitStatus.Ended(visit)
        else -> snapshot.requests.find {
            it.stopVisitId == visit.id && it.isOpen
        }?.let {
            VisitStatus.Open(visit, it)
        } ?: if (visit.sequence <= trip.lastPassedSequence) {
            VisitStatus.Passed(visit)
        } else {
            VisitStatus.Available(visit)
        }
    }
}

fun statusText(status: VisitStatus): String = when (status) {
    VisitStatus.NoVisit -> "There is no active laboratory trip. Press Refresh."
    is VisitStatus.Ended -> "The demonstration trip has ended."
    is VisitStatus.Passed -> "The bus has already passed this stop."
    is VisitStatus.Open -> "You already have a request at this stop: ${stateLabel(status.request.state)}."
    is VisitStatus.Available -> "You can request assistance at this stop."
}

/**
 * The stop with this CRTM code, from the downloaded list or its local copy.
 */
fun findStop(state: StopsState, code: String): Stop? =
    (state as? StopsState.Ready)?.copy?.stops?.find { it.code == code }
