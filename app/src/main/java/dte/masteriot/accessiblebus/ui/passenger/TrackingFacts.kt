package dte.masteriot.accessiblebus.ui.passenger

import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One of the four facts of a request. [done] is false while the fact has not happened yet. */
data class Fact(val title: String, val text: String, val done: Boolean)

/**
 * The four separate facts of the proposal (p. 2 and 5): the request was registered, the bus
 * terminal received it, the driver decided, and the request was closed. Receipt never means
 * that a person read it; a decision is never inferred from the receipt. Texts follow
 * "Mensajes que no engañan" (p. 17).
 */
fun trackingFacts(request: AssistanceRequest): List<Fact> {
    val state = request.state
    // From PENDING the only way to ACCEPTED is accept, and every change adds 1 to the version,
    // so a request that expired with version 3 or more had been accepted before.
    val acceptedBefore = state == RequestState.ACCEPTED || state == RequestState.COMPLETED ||
        ((state == RequestState.EXPIRED || state == RequestState.CANCELLED) && request.version >= 3)

    val registered = Fact("Solicitud registrada", "Registrada a las ${time(request.createdAt)}.", done = true)

    val received = if (request.received) {
        Fact("Recibida por el terminal del autobús", "El terminal la ha recibido. Esto no significa que el conductor la haya leído.", true)
    } else {
        Fact("Recibida por el terminal del autobús", "Aún no consta que el terminal la haya recibido.", false)
    }

    val decision = when {
        state == RequestState.REJECTED -> Fact("Decisión del conductor", "Tu solicitud no se ha podido atender.", true)
        acceptedBefore -> Fact("Decisión del conductor", "El conductor ha confirmado tu solicitud.", true)
        state == RequestState.PENDING -> Fact("Decisión del conductor", "Pendiente de respuesta del conductor.", false)
        else -> Fact("Decisión del conductor", "No hubo decisión del conductor.", false)
    }

    val closure = when (state) {
        RequestState.PENDING, RequestState.ACCEPTED -> Fact("Cierre", "La solicitud sigue abierta.", false)
        RequestState.COMPLETED -> Fact("Cierre", "El conductor ha marcado la asistencia como realizada.", true)
        RequestState.CANCELLED -> Fact("Cierre", "Cancelaste la solicitud.", true)
        RequestState.REJECTED -> Fact("Cierre", "Solicitud cerrada. Motivo: ${closeLabel(request.closeReason) ?: "no indicado"}.", true)
        RequestState.EXPIRED -> Fact(
            "Cierre",
            when (request.closeReason) {
                "STOP_PASSED" -> "La parada ha quedado atrás sin confirmación; solicitud cerrada."
                "TRIP_ENDED" -> "El viaje de demostración ha finalizado; consulta el estado."
                else -> "Solicitud cerrada."
            },
            true,
        )
    }
    return listOf(registered, received, decision, closure)
}

/** True when the trip of this request is over: it ended, or the demo moved to a new trip. */
fun tripFinished(snapshot: Snapshot, request: AssistanceRequest): Boolean {
    val trip = snapshot.trip
    return request.closeReason == "TRIP_ENDED" ||
        (trip != null && trip.id == request.tripId && trip.state == TripState.ENDED) ||
        (trip != null && trip.id != request.tripId)
}

/** "2026-11-08T12:16:39.134962Z" as the local "13:16". */
private fun time(iso: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(parser.parse(iso.take(19))!!)
}.getOrDefault(iso)
