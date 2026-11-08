package dte.masteriot.accessiblebus.core.net

import java.io.File
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The models read the samples captured from the real API (K6) without changes. */
class ModelsTest {

    private fun sample(name: String) = File("src/main/assets/samples/$name").readText()

    @Test
    fun syncSampleIsASnapshot() {
        val snapshot = ApiJson.decodeFromString(Snapshot.serializer(), sample("sync.json"))

        val trip = snapshot.trip!!
        assertEquals("BUS-DEMO-01", trip.vehicleId)
        assertEquals(TripState.ACTIVE, trip.state)
        assertEquals(0, trip.lastPassedSequence)
        assertEquals(listOf("456", "459", "460", "461"), snapshot.stopVisits.map { it.physicalStopCode })
        assertEquals(listOf(1, 2, 3, 4), snapshot.stopVisits.map { it.sequence })

        val request = snapshot.requests.single()
        assertEquals(RequestState.PENDING, request.state)
        assertEquals(1, request.version)
        assertFalse(request.received)
        assertNull(request.closeReason)
        assertTrue(request.isOpen)
        assertEquals(snapshot.stopVisits[1].id, request.stopVisitId)
    }

    @Test
    fun requestAcceptedEventCarriesTheFullRequest() {
        val event = ApiJson.decodeFromString(Event.serializer(), sample("event_request_accepted.json"))

        assertEquals(3, event.schemaVersion)
        assertEquals("request.accepted", event.eventType)
        assertEquals(2, event.version)
        val request = event.requestPayload()
        assertEquals(event.requestId, request.id)
        assertEquals(RequestState.ACCEPTED, request.state)
        assertEquals(2, request.version)
        assertTrue(request.received)
    }

    @Test
    fun otherEventsDecode() {
        val progressed = ApiJson.decodeFromString(Event.serializer(), sample("event_trip_progressed.json"))
        assertNull(progressed.requestId)
        assertNull(progressed.version)
        assertEquals(1, progressed.tripPayload().lastPassedSequence)

        val created = ApiJson.decodeFromString(Event.serializer(), sample("event_request_created.json"))
        assertEquals(RequestState.PENDING, created.requestPayload().state)

        val receipt = ApiJson.decodeFromString(Event.serializer(), sample("event_receipt_recorded.json"))
        assertEquals("request.receipt_recorded", receipt.eventType)
        assertTrue(receipt.payload.jsonObject.getValue("received").jsonPrimitive.boolean)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = sample("sync.json").replaceFirst("{", """{"addedLater": 1,""")
        assertEquals(4, ApiJson.decodeFromString(Snapshot.serializer(), text).stopVisits.size)
    }
}
