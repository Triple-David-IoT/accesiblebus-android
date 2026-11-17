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
            listOf("Solicitud registrada", "Recibida por el terminal del autobús", "Decisión del conductor", "Cierre"),
            trackingFacts(pending).map { it.title },
        )
        assertEquals(listOf(true, false, false, false), done(pending))
    }

    @Test
    fun receiptIsNotADecision() {
        val received = pending.copy(received = true)
        assertEquals(listOf(true, true, false, false), done(received))
        assertTrue(texts(received)[1].contains("no significa que el conductor la haya leído"))
        assertEquals("Pendiente de respuesta del conductor.", texts(received)[2])
    }

    @Test
    fun proposalMessages() {
        // Propuesta p. 17, "Mensajes que no engañan".
        assertEquals("El conductor ha confirmado tu solicitud.", texts(pending.to(RequestState.ACCEPTED, 2))[2])
        assertEquals("Tu solicitud no se ha podido atender.", texts(pending.to(RequestState.REJECTED, 2, "UNSAFE_STOP"))[2])
        assertEquals(
            "La parada ha quedado atrás sin confirmación; solicitud cerrada.",
            texts(pending.to(RequestState.EXPIRED, 2, "STOP_PASSED"))[3],
        )
        assertEquals(
            "El viaje de demostración ha finalizado; consulta el estado.",
            texts(pending.to(RequestState.EXPIRED, 2, "TRIP_ENDED"))[3],
        )
        // Completing is an explicit action of the driver.
        assertEquals("El conductor ha marcado la asistencia como realizada.", texts(pending.to(RequestState.COMPLETED, 3))[3])
    }

    @Test
    fun closures() {
        val rejected = pending.to(RequestState.REJECTED, 2, "RAMP_UNAVAILABLE")
        assertEquals("Solicitud cerrada. Motivo: Rampa no disponible.", texts(rejected)[3])
        assertEquals(listOf(true, false, true, true), done(rejected))

        assertEquals("Cancelaste la solicitud.", texts(pending.to(RequestState.CANCELLED, 2))[3])
        assertEquals("No hubo decisión del conductor.", texts(pending.to(RequestState.CANCELLED, 2))[2])
        // Accepted first (version 2), then cancelled or expired (version 3): the decision stays.
        assertEquals("El conductor ha confirmado tu solicitud.", texts(pending.to(RequestState.CANCELLED, 3))[2])
        assertEquals("El conductor ha confirmado tu solicitud.", texts(pending.to(RequestState.EXPIRED, 3, "TRIP_ENDED"))[2])
        assertEquals("No hubo decisión del conductor.", texts(pending.to(RequestState.EXPIRED, 2, "STOP_PASSED"))[2])
    }

    @Test
    fun finishedTrip() {
        assertFalse(tripFinished(snapshot, pending))
        assertTrue(tripFinished(snapshot.copy(trip = snapshot.trip!!.copy(state = TripState.ENDED)), pending))
        assertTrue(tripFinished(snapshot.copy(trip = snapshot.trip!!.copy(id = "new-trip")), pending))
        assertTrue(tripFinished(snapshot, pending.to(RequestState.EXPIRED, 2, "TRIP_ENDED")))
    }
}
