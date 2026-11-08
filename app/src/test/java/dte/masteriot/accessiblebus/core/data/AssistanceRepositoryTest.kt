package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiResult
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RequestState
import dte.masteriot.accessiblebus.core.net.SampleApi
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.TripState
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class MemoryKeys : CommandKeys {
    val map = mutableMapOf<String, String>()
    override fun get(commandId: String) = map[commandId]
    override fun put(commandId: String, key: String) { map[commandId] = key }
    override fun remove(commandId: String) { map.remove(commandId) }
    override fun all() = map.keys.toSet()
}

/** The sample server, which can be down or lose the answer after running the command. */
private class FlakyApi(val server: SampleApi) : ApiSource by server {
    var down = false
    var loseAnswer = false
    val createKeys = mutableListOf<String>()

    override suspend fun createRequest(key: String, stopVisitId: String): ApiResult<AssistanceRequest> {
        createKeys += key
        if (down) return ApiResult.Failure("Connection refused", retryable = true)
        val result = server.createRequest(key, stopVisitId)
        return if (loseAnswer) ApiResult.Failure("timeout", retryable = true) else result
    }
}

class AssistanceRepositoryTest {

    private val server = SampleApi { name -> File("src/main/assets/samples/$name").readText() }
    private val api = FlakyApi(server)
    private val keys = MemoryKeys()
    private var counter = 0
    private val repository = AssistanceRepository(api, keys, newKey = { "key-${++counter}" })
    private lateinit var visit: String

    @Before
    fun reset() = runTest {
        visit = (server.resetDemo("reset") as ApiResult.Success).value.stopVisits[1].id
        repository.refresh()
    }

    private suspend fun serverRequests() = (server.sync("mine") as ApiResult.Success).value.requests

    @Test
    fun apiDownThenRetrySendsOneRequestWithTheSameKey() = runTest {
        api.down = true
        val first = repository.create(visit)
        assertTrue((first as ApiResult.Failure).retryable)
        val id = CommandIds.create(visit)
        assertTrue(repository.commands.value[id] is CommandState.Unconfirmed)
        assertEquals("key-1", keys.get(id))

        api.down = false
        val second = repository.create(visit) as ApiResult.Success
        assertEquals(listOf("key-1", "key-1"), api.createKeys)
        assertEquals(1, serverRequests().size)
        assertEquals(RequestState.PENDING, repository.snapshot.value.requests.single { it.id == second.value.id }.state)
        assertNull(keys.get(id))
        assertNull(repository.commands.value[id])
    }

    @Test
    fun lostAnswerIsRecoveredWithTheSameKey() = runTest {
        api.loseAnswer = true
        repository.create(visit)
        assertEquals(1, serverRequests().size)
        assertTrue(repository.snapshot.value.requests.isEmpty())

        api.loseAnswer = false
        val again = repository.create(visit) as ApiResult.Success
        assertEquals(serverRequests().single().id, again.value.id)
        assertEquals(1, serverRequests().size)
    }

    @Test
    fun conflictAppliesTheCurrentResourceAndTheNextTryUsesANewKey() = runTest {
        val created = (repository.create(visit) as ApiResult.Success).value
        server.acceptRequest("driver-accept", created.id, 1)

        val stale = repository.cancel(created.id) as ApiResult.Conflict
        assertEquals("VERSION_CONFLICT", stale.code)
        val seen = repository.snapshot.value.requests.single()
        assertEquals(RequestState.ACCEPTED to 2, seen.state to seen.version)
        assertTrue(repository.commands.value[CommandIds.request("cancel", created)] is CommandState.Refused)
        assertTrue(keys.map.isEmpty())

        val cancelled = (repository.cancel(created.id) as ApiResult.Success).value
        assertEquals(RequestState.CANCELLED to 3, cancelled.state to cancelled.version)
    }

    @Test
    fun keysLeftByAPreviousRunAreReused() = runTest {
        api.down = true
        repository.create(visit)

        api.down = false
        val restarted = AssistanceRepository(api, keys, newKey = { "never-used" })
        assertTrue(restarted.commands.value[CommandIds.create(visit)] is CommandState.Unconfirmed)
        restarted.refresh()
        restarted.create(visit)
        assertEquals(listOf("key-1", "key-1"), api.createKeys)
    }

    @Test
    fun tripCommandsMoveTheTrip() = runTest {
        val created = (repository.create(visit) as ApiResult.Success).value
        assertEquals(1, (repository.advance() as ApiResult.Success).value.lastPassedSequence)
        assertEquals(TripState.ENDED, (repository.end() as ApiResult.Success).value.state)
        repository.refresh()
        assertEquals(TripState.ENDED, repository.snapshot.value.trip!!.state)
        assertEquals("TRIP_ENDED", repository.snapshot.value.requests.single { it.id == created.id }.closeReason)
    }

    @Test
    fun mergeKeepsTheNewestState() = runTest {
        val start = repository.snapshot.value
        val created = (repository.create(visit) as ApiResult.Success).value
        val accepted = created.copy(state = RequestState.ACCEPTED, version = 2, received = false)
        val stale = created.copy(received = true)

        val merged = AssistanceRepository.mergeRequests(listOf(accepted), listOf(stale)).single()
        assertEquals(RequestState.ACCEPTED to true, merged.state to merged.received)

        val ended = start.trip!!.copy(state = TripState.ENDED, lastPassedSequence = 2)
        val behind = start.trip!!.copy(lastPassedSequence = 1)
        val trip = AssistanceRepository.mergeTrip(ended, behind)
        assertEquals(TripState.ENDED to 2, trip.state to trip.lastPassedSequence)

        val other = Snapshot(trip = start.trip!!.copy(id = "another-trip"))
        assertEquals(other, AssistanceRepository.merge(start, other))
    }
}
