package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiJson
import dte.masteriot.accessiblebus.core.net.ApiResult
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.Event
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.SampleApi
import dte.masteriot.accessiblebus.core.net.Trip
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFeed : EventFeed {
    override val connected = MutableStateFlow(false)
    var topic: String? = null
    lateinit var deliver: (String) -> Unit
    lateinit var reconnected: () -> Unit

    override suspend fun start(topic: String, onMessage: (String) -> Unit, onReconnected: () -> Unit): Boolean {
        this.topic = topic
        deliver = onMessage
        reconnected = onReconnected
        connected.value = true
        return true
    }
}

private class Keys : CommandKeys {
    val map = mutableMapOf<String, String>()
    override fun get(commandId: String) = map[commandId]
    override fun put(commandId: String, key: String): Boolean { map[commandId] = key; return true }
    override fun remove(commandId: String): Boolean { map.remove(commandId); return true }
    override fun all() = map.keys.toSet()
}

class RepositoryEventsTest {

    private val server = SampleApi { name -> File("src/main/assets/samples/$name").readText() }
    private val feed = FakeFeed()
    private val announced = MemoryAnnouncedDecisions()
    private val heard = mutableListOf<DecisionEvent>()

    private fun TestScope.repository() = AssistanceRepository(
        api = server,
        keys = Keys(),
        events = feed,
        eventsTopic = "users/passenger-1/events",
        announcer = { heard += it },
        announced = announced,
        scope = this,
    )

    private fun event(type: String, request: AssistanceRequest, id: String = UUID.randomUUID().toString()) =
        ApiJson.encodeToString(
            Event.serializer(),
            Event(3, id, type, request.tripId, request.id, request.version, ApiJson.encodeToJsonElement(AssistanceRequest.serializer(), request)),
        )

    private fun tripEvent(type: String, trip: Trip) =
        ApiJson.encodeToString(
            Event.serializer(),
            Event(3, UUID.randomUUID().toString(), type, trip.id, payload = ApiJson.encodeToJsonElement(Trip.serializer(), trip)),
        )

    private suspend fun start(): Pair<String, String> {
        val reset = (server.resetDemo("reset-${UUID.randomUUID()}") as ApiResult.Success).value
        return reset.trip.id to reset.stopVisits[1].id
    }

    @Test
    fun eventsApplyOnceAndAnnounceOnce() = runTest {
        val (_, visit) = start()
        val repository = repository()
        repository.startEvents()
        advanceUntilIdle()
        assertEquals("users/passenger-1/events", feed.topic)
        assertTrue(repository.live.value)

        val created = (repository.create(visit) as ApiResult.Success).value
        val accepted = (server.acceptRequest("driver", created.id, 1) as ApiResult.Success).value
        val message = event("request.accepted", accepted)

        feed.deliver(message)
        feed.deliver(message)
        assertEquals(RequestState.ACCEPTED, repository.snapshot.value.requests.single().state)
        assertEquals(listOf(DecisionEvent(created.id, 2, RequestState.ACCEPTED, null)), heard)

        // The same decision again by /sync and by a second copy with another eventId.
        repository.refresh()
        feed.deliver(event("request.accepted", accepted))
        assertEquals(1, heard.size)

        // An older event arriving late never hides the newer state.
        feed.deliver(event("request.created", created))
        assertEquals(RequestState.ACCEPTED, repository.snapshot.value.requests.single().state)
        assertTrue(heard.none { it.state == RequestState.PENDING })
    }

    @Test
    fun reconnectingRecoversWhatHappenedOffline() = runTest {
        val (_, visit) = start()
        val repository = repository()
        repository.startEvents()
        advanceUntilIdle()
        val created = (repository.create(visit) as ApiResult.Success).value

        // Offline: the driver accepts and the app gets no event.
        feed.connected.value = false
        val accepted = (server.acceptRequest("driver", created.id, 1) as ApiResult.Success).value
        assertEquals(RequestState.PENDING, repository.snapshot.value.requests.single().state)

        feed.connected.value = true
        feed.reconnected()
        advanceUntilIdle()
        assertEquals(RequestState.ACCEPTED, repository.snapshot.value.requests.single().state)
        assertEquals(1, heard.size)

        // The broker may still deliver the event later: no second announcement.
        feed.deliver(event("request.accepted", accepted))
        assertEquals(1, heard.size)
    }

    @Test
    fun announcementsSurviveARestart() = runTest {
        val (_, visit) = start()
        val first = repository()
        first.refresh()
        val created = (first.create(visit) as ApiResult.Success).value
        server.rejectRequest("driver", created.id, 1, dte.masteriot.accessiblebus.core.net.RejectReason.UNSAFE_STOP)
        first.refresh()
        assertEquals(listOf(RequestState.REJECTED), heard.map { it.state })
        assertEquals("UNSAFE_STOP", heard.single().reason)

        val second = repository()
        second.refresh()
        assertEquals(1, heard.size)
    }

    @Test
    fun receiptAndTripEvents() = runTest {
        val (trip, visit) = start()
        val repository = repository()
        repository.startEvents()
        advanceUntilIdle()
        val created = (repository.create(visit) as ApiResult.Success).value

        val receipt = buildJsonObject { put("received", JsonPrimitive(true)) }
        feed.deliver(
            ApiJson.encodeToString(
                Event.serializer(),
                Event(3, UUID.randomUUID().toString(), "request.receipt_recorded", trip, created.id, 1, receipt),
            ),
        )
        assertTrue(repository.snapshot.value.requests.single().received)

        val moved = (server.advanceTrip("driver-advance", trip, 0) as ApiResult.Success).value
        feed.deliver(tripEvent("trip.progressed", moved))
        assertEquals(1, repository.snapshot.value.trip!!.lastPassedSequence)

        // A trip the app does not know: /sync brings the new trip and its visits.
        val (newTrip, _) = start()
        feed.deliver(tripEvent("trip.progressed", moved.copy(id = newTrip)))
        advanceUntilIdle()
        assertEquals(newTrip, repository.snapshot.value.trip!!.id)
        assertTrue(repository.snapshot.value.requests.isEmpty())

        feed.deliver("not json")
        feed.deliver("""{"schemaVersion":3,"eventId":"x","eventType":"other","tripId":"t","payload":{}}""")
        assertEquals(newTrip, repository.snapshot.value.trip!!.id)
    }
}
