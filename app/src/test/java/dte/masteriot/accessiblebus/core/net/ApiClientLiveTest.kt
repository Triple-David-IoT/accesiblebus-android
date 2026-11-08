package dte.masteriot.accessiblebus.core.net

import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs ApiClient against a running API. Skipped unless API_URL is set, for example:
 * API_URL=http://192.168.1.50:8000 ./gradlew :app:testDebugUnitTest --tests '*ApiClientLiveTest'
 * It resets the demo trip, so never run it during a demo.
 */
class ApiClientLiveTest {

    private val url = System.getenv("API_URL").orEmpty()
    private val run = UUID.randomUUID().toString()

    private fun client(token: String) = ApiClient(url) { token }

    private fun <T> ApiResult<T>.value(): T = (this as? ApiResult.Success<T>)?.value ?: error("Unexpected $this")

    @Test
    fun mainFlow() = runTest {
        assumeTrue("API_URL not set", url.isNotEmpty())
        val passenger = client("demo-pasajero-1")
        val driver = client("demo-conductor-1")

        val start = client("demo-operador-1").resetDemo("$run-reset").value()
        val visit = start.stopVisits[1]

        val created = passenger.createRequest("$run-create", visit.id).value()
        assertEquals(RequestState.PENDING to 1, created.state to created.version)
        assertEquals(created, passenger.createRequest("$run-create", visit.id).value())
        val reused = passenger.createRequest("$run-create", start.stopVisits[2].id) as ApiResult.Conflict
        assertEquals("IDEMPOTENCY_CONFLICT", reused.code)

        val accepted = driver.acceptRequest("$run-accept", created.id, 1).value()
        assertEquals(RequestState.ACCEPTED, accepted.state)
        val stale = passenger.cancelRequest("$run-cancel", created.id, 1) as ApiResult.Conflict
        assertEquals("VERSION_CONFLICT", stale.code)
        assertEquals(accepted.version, stale.current!!.version)
        assertEquals(RequestState.COMPLETED, driver.completeRequest("$run-complete", created.id, 2).value().state)

        val mine = passenger.sync("mine").value()
        assertEquals(start.trip.id, mine.trip!!.id)
        assertEquals(RequestState.COMPLETED, mine.requests.single { it.id == created.id }.state)
        assertEquals(4, driver.sync("vehicle", "BUS-DEMO-01").value().stopVisits.size)
        assertEquals(start.stopVisits, passenger.stopVisits(start.trip.id).value())
        assertEquals(403, (passenger.acceptRequest("$run-forbidden", created.id, 3) as ApiResult.Failure).httpStatus)
    }
}
