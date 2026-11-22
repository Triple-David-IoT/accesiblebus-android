
package dte.masteriot.accessiblebus.ui.passenger

import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * One of the four facts of a request. [done] is false while the fact has not happened yet.
 */
data class Fact(val title: String, val text: String, val done: Boolean)

/**
 * Four separate facts: the request was registered, the bus
 * terminal received it, the driver decided, and the request was closed. Receipt never means
 * that a person read it; a decision is never inferred from the receipt, and no text promises more
 * than the system knows.
 */
fun trackingFacts(request: AssistanceRequest): List<Fact> {
    val state = request.state

    // From PENDING the only way to ACCEPTED is accept, and every change adds 1 to the version,
    // so a request that expired with version 3 or more had been accepted before.
    val acceptedBefore = state == RequestState.ACCEPTED || state == RequestState.COMPLETED ||
            ((state == RequestState.EXPIRED || state == RequestState.CANCELLED) && request.version >= 3)

    val registered = Fact(
        "Request registered",
        "Registered at ${time(request.createdAt)}.",
        done = true
    )

    val received = if (request.received) {
        Fact(
            "Received by the bus terminal",
            "The terminal has received it. This does not mean that the driver has read it.",
            true
        )
    } else {
        Fact(
            "Received by the bus terminal",
            "There is no confirmation that the terminal has received it yet.",
            false
        )
    }

    val decision = when {
        state == RequestState.REJECTED ->
            Fact("Driver's decision", "Your request could not be fulfilled.", true)

        acceptedBefore ->
            Fact("Driver's decision", "The driver has confirmed your request.", true)

        state == RequestState.PENDING ->
            Fact("Driver's decision", "Waiting for the driver's response.", false)

        else ->
            Fact("Driver's decision", "There was no decision from the driver.", false)
    }

    val closure = when (state) {
        RequestState.PENDING, RequestState.ACCEPTED ->
            Fact("Closure", "The request is still open.", false)

        RequestState.COMPLETED ->
            Fact("Closure", "The driver has marked the assistance as completed.", true)

        RequestState.CANCELLED ->
            Fact("Closure", "You cancelled the request.", true)

        RequestState.REJECTED ->
            Fact(
                "Closure",
                "Request closed. Reason: ${closeLabel(request.closeReason) ?: "not specified"}.",
                true
            )

        RequestState.EXPIRED ->
            Fact(
                "Closure",
                when (request.closeReason) {
                    "STOP_PASSED" ->
                        "The bus passed the stop without confirmation; the request was closed."

                    "TRIP_ENDED" ->
                        "The demonstration trip has ended; check the status."

                    else ->
                        "Request closed."
                },
                true
            )
    }

    return listOf(registered, received, decision, closure)
}

/**
 * True when the trip of this request is over: it ended, or the demo moved to a new trip.
 */
fun tripFinished(snapshot: Snapshot, request: AssistanceRequest): Boolean {
    val trip = snapshot.trip
    return request.closeReason == "TRIP_ENDED" ||
            (trip != null && trip.id == request.tripId && trip.state == TripState.ENDED) ||
            (trip != null && trip.id != request.tripId)
}

/**
 * Converts "2026-11-08T12:16:39.134962Z" to local time, e.g. "13:16".
 */
private fun time(iso: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(parser.parse(iso.take(19))!!)
}.getOrDefault(iso)
