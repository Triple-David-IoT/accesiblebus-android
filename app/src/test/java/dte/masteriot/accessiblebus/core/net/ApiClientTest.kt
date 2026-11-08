package dte.masteriot.accessiblebus.core.net

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private val server = MockWebServer()
    private lateinit var api: ApiClient
    private val request = File("src/main/assets/samples/event_request_accepted.json").readText()
        .let { ApiJson.decodeFromString(Event.serializer(), it).payload.toString() }

    @Before
    fun start() {
        server.start()
        api = ApiClient(server.url("/").toString()) { "demo-conductor-1" }
    }

    @After
    fun stop() = server.shutdown()

    private fun answer(status: Int, body: String) =
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))

    @Test
    fun commandSendsTokenKeyAndBody() = runTest {
        answer(200, request)

        val result = api.acceptRequest("key-1", "cccccccc-cccc-4ccc-8ccc-cccccccccccc", 1)

        assertEquals(RequestState.ACCEPTED, (result as ApiResult.Success).value.state)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/requests/cccccccc-cccc-4ccc-8ccc-cccccccccccc/accept", sent.path)
        assertEquals("Bearer demo-conductor-1", sent.getHeader("Authorization"))
        assertEquals("key-1", sent.getHeader("Idempotency-Key"))
        assertEquals("""{"expectedVersion":1}""", sent.body.readUtf8())
    }

    @Test
    fun createSendsTheWholeBody() = runTest {
        answer(201, request)

        api.createRequest("key-0", "22222222-2222-4222-8222-222222222222")

        assertEquals(
            """{"stopVisitId":"22222222-2222-4222-8222-222222222222","assistanceType":"RAMP_BOARDING"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun syncSendsScopeAndVehicle() = runTest {
        answer(200, File("src/main/assets/samples/sync.json").readText())

        val result = api.sync("vehicle", "BUS-DEMO-01")

        assertEquals(4, (result as ApiResult.Success).value.stopVisits.size)
        assertEquals("/sync?scope=vehicle&vehicleId=BUS-DEMO-01", server.takeRequest().path)
    }

    @Test
    fun conflictCarriesTheCurrentResource() = runTest {
        answer(409, """{"code":"VERSION_CONFLICT","message":"The request has changed","currentResource":$request}""")

        val result = api.cancelRequest("key-2", "cccccccc-cccc-4ccc-8ccc-cccccccccccc", 1) as ApiResult.Conflict

        assertEquals("VERSION_CONFLICT", result.code)
        assertEquals(2, result.current!!.version)
    }

    @Test
    fun idempotencyConflictHasNoResource() = runTest {
        answer(409, """{"code":"IDEMPOTENCY_CONFLICT","message":"Key used for another command"}""")

        val result = api.createRequest("key-3", "22222222-2222-4222-8222-222222222222") as ApiResult.Conflict

        assertEquals("IDEMPOTENCY_CONFLICT", result.code)
        assertNull(result.current)
    }

    @Test
    fun serverErrorsAndTimeoutsAreRetryable() = runTest {
        answer(503, "")
        val busy = api.endTrip("key-4", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa") as ApiResult.Failure
        assertTrue(busy.retryable)
        assertEquals(503, busy.httpStatus)

        server.shutdown()
        val down = api.endTrip("key-4", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa") as ApiResult.Failure
        assertTrue(down.retryable)
        assertNull(down.httpStatus)
    }

    @Test
    fun clientErrorsAreNotRetryable() = runTest {
        answer(403, """{"code":"FORBIDDEN","message":"You cannot accept this request"}""")
        val forbidden = api.acceptRequest("key-5", "cccccccc-cccc-4ccc-8ccc-cccccccccccc", 1) as ApiResult.Failure
        assertFalse(forbidden.retryable)
        assertEquals("FORBIDDEN: You cannot accept this request", forbidden.message)

        answer(422, """{"detail":[{"loc":["body","expectedVersion"],"msg":"too small","type":"greater_than_equal"}]}""")
        val invalid = api.acceptRequest("key-6", "cccccccc-cccc-4ccc-8ccc-cccccccccccc", 0) as ApiResult.Failure
        assertFalse(invalid.retryable)
        assertEquals(422, invalid.httpStatus)
    }
}
