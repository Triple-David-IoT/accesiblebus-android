package dte.masteriot.accessiblebus.ui.passenger

import dte.masteriot.accessiblebus.core.net.ApiJson
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingFactsTest {

    private val snapshot = ApiJson.decodeFromString(Snapshot.serializer(), File("src/main/assets/samples/sync.json").readText())
    private val pending = snapshot.requests.single()

    private fun AssistanceRequest.to(state: RequestState, version: Int, reason: String? = null, received: Boolean = this.received) =
        copy(state = state, version = version, closeReason = reason, received = received)

    private fun texts(request: AssistanceRequest) = trackingFacts(request).map { it.text }
    private fun done(request: AssistanceRequest) = trackingFacts(request).map { it.done }

    @Test
    fun fourSeparateFacts() {
        assertEquals(
            listOf("Request registered", "Received by the bus terminal", "Driver's decision", "Closure"),
            trackingFacts(pending).map { it.title },
        )
        assertEquals(listOf(true, false, false, false), done(pending))
    }

    @Test
    fun receiptIsNotADecision() {
        val received = pending.copy(received = true)
        assertEquals(listOf(true, true, false, false), done(received))
        assertTrue(texts(received)[1].contains("does not mean that the driver has read it"))
        assertEquals("Waiting for the driver's response.", texts(received)[2])
    }

    @Test
    fun proposalMessages() {
        // Proposal p. 17, "Messages that do not mislead".
        assertEquals("The driver has confirmed your request.", texts(pending.to(RequestState.ACCEPTED, 2))[2])
        assertEquals("Your request could not be fulfilled.", texts(pending.to(RequestState.REJECTED, 2, "UNSAFE_STOP"))[2])
        assertEquals(
            "The bus passed the stop without confirmation; the request was closed.",
            texts(pending.to(RequestState.EXPIRED, 2, "STOP_PASSED"))[3],
        )
        assertEquals(
            "The demonstration trip has ended; check the status.",
            texts(pending.to(RequestState.EXPIRED, 2, "TRIP_ENDED"))[3],
        )
        // Completing is an explicit action of the driver.
        assertEquals("The driver has marked the assistance as completed.", texts(pending.to(RequestState.COMPLETED, 3))[3])
    }

    @Test
    fun closures() {
        val rejected = pending.to(RequestState.REJECTED, 2, "RAMP_UNAVAILABLE")
        assertEquals("Request closed. Reason: Ramp unavailable.", texts(rejected)[3])
        assertEquals(listOf(true, false, true, true), done(rejected))

        assertEquals("You cancelled the request.", texts(pending.to(RequestState.CANCELLED, 2))[3])
        assertEquals("There was no decision from the driver.", texts(pending.to(RequestState.CANCELLED, 2))[2])
        // Accepted first (version 2), then cancelled or expired (version 3): the decision stays.
        assertEquals("The driver has confirmed your request.", texts(pending.to(RequestState.CANCELLED, 3))[2])
        assertEquals("The driver has confirmed your request.", texts(pending.to(RequestState.EXPIRED, 3, "TRIP_ENDED"))[2])
        assertEquals("There was no decision from the driver.", texts(pending.to(RequestState.EXPIRED, 2, "STOP_PASSED"))[2])
    }

    @Test
    fun finishedTrip() {
        assertFalse(tripFinished(snapshot, pending))
        assertTrue(tripFinished(snapshot.copy(trip = snapshot.trip!!.copy(state = TripState.ENDED)), pending))
        assertTrue(tripFinished(snapshot.copy(trip = snapshot.trip!!.copy(id = "new-trip")), pending))
        assertTrue(tripFinished(snapshot, pending.to(RequestState.EXPIRED, 2, "TRIP_ENDED")))
    }
}
