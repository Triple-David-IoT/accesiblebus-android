package dte.masteriot.accessiblebus.core.net

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleApiTest {

    private val api = SampleApi { name -> File("src/main/assets/samples/$name").readText() }

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success<T>).value

    @Test
    fun commandsFollowTheApiRules() = runTest {
        val start = api.resetDemo("reset").value()
        assertTrue(api.sync("mine").value().requests.isEmpty())
        val visit = start.stopVisits[1]

        val created = api.createRequest("k1", visit.id).value()
        assertEquals(RequestState.PENDING, created.state)
        assertEquals(created, api.createRequest("k1", visit.id).value())
        val busy = api.createRequest("k2", visit.id) as ApiResult.Conflict
        assertEquals("ACTIVE_REQUEST_EXISTS", busy.code)

        val accepted = api.acceptRequest("k3", created.id, 1).value()
        assertEquals(RequestState.ACCEPTED to 2, accepted.state to accepted.version)
        val stale = api.cancelRequest("k4", created.id, 1) as ApiResult.Conflict
        assertEquals("VERSION_CONFLICT", stale.code)
        assertEquals(accepted, stale.current)

        val completed = api.completeRequest("k5", created.id, 2).value()
        assertEquals(RequestState.COMPLETED to 3, completed.state to completed.version)
        assertEquals("INVALID_TRANSITION", (api.cancelRequest("k6", created.id, 3) as ApiResult.Conflict).code)
        assertEquals(completed, api.sync("mine").value().requests.single())
    }

    @Test
    fun passingAStopExpiresItsPendingRequest() = runTest {
        val start = api.resetDemo("reset").value()
        val first = api.createRequest("k1", start.stopVisits[0].id).value()

        val trip = api.advanceTrip("k2", start.trip.id, 0).value()
        assertEquals(1, trip.lastPassedSequence)
        val expired = api.getRequest(first.id).value()
        assertEquals(RequestState.EXPIRED, expired.state)
        assertEquals("STOP_PASSED", expired.closeReason)
        assertEquals("VISIT_ALREADY_PASSED", (api.createRequest("k3", start.stopVisits[0].id) as ApiResult.Conflict).code)

        val second = api.createRequest("k4", start.stopVisits[2].id).value()
        api.endTrip("k5", start.trip.id).value()
        assertEquals("TRIP_ENDED", api.getRequest(second.id).value().closeReason)
        val late = api.acceptRequest("k6", second.id, 2) as ApiResult.Conflict
        assertEquals("TRIP_NOT_ACTIVE", late.code)
        assertFalse((api.getRequest("missing") as ApiResult.Failure).retryable)
    }
}
