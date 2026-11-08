package dte.masteriot.accessiblebus.core.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline ApiSource that starts from assets/samples/sync.json and keeps the changes in memory,
 * with the same rules as the API for versions, transitions and idempotency keys. It lets the
 * screens be built and shown without the server. To be removed in I1.
 *
 * [read] returns the text of a file in assets/samples, for example "sync.json".
 */
class SampleApi(private val read: (String) -> String) : ApiSource {

    private val mutex = Mutex()
    private var snapshot = load()
    private val answers = mutableMapOf<String, ApiResult<*>>()

    private fun load(): Snapshot = ApiJson.decodeFromString(Snapshot.serializer(), read("sync.json"))

    override suspend fun sync(scope: String, vehicleId: String?): ApiResult<Snapshot> = mutex.withLock { ApiResult.Success(snapshot) }

    override suspend fun getRequest(requestId: String): ApiResult<AssistanceRequest> = mutex.withLock {
        snapshot.requests.find { it.id == requestId }?.let { ApiResult.Success(it) } ?: notFound()
    }

    override suspend fun stopVisits(tripId: String): ApiResult<List<StopVisit>> = mutex.withLock {
        ApiResult.Success(snapshot.stopVisits.filter { it.tripId == tripId })
    }

    override suspend fun createRequest(key: String, stopVisitId: String): ApiResult<AssistanceRequest> = command(key) {
        val trip = snapshot.trip
        val visit = snapshot.stopVisits.find { it.id == stopVisitId }
        when {
            trip == null || visit == null -> notFound()
            trip.state != TripState.ACTIVE -> ApiResult.Conflict("TRIP_NOT_ACTIVE", "The trip has ended", null)
            visit.sequence <= trip.lastPassedSequence ->
                ApiResult.Conflict("VISIT_ALREADY_PASSED", "The bus already passed this stop", null)
            snapshot.requests.any { it.stopVisitId == stopVisitId && it.isOpen } ->
                ApiResult.Conflict("ACTIVE_REQUEST_EXISTS", "There is already an open request for this stop", null)
            else -> {
                val now = now()
                val request = AssistanceRequest(
                    id = UUID.randomUUID().toString(),
                    tripId = trip.id,
                    vehicleId = trip.vehicleId,
                    ownerSubject = "passenger-1",
                    stopVisitId = visit.id,
                    sequence = visit.sequence,
                    physicalStopCode = visit.physicalStopCode,
                    assistanceType = AssistanceType.RAMP_BOARDING,
                    state = RequestState.PENDING,
                    version = 1,
                    received = false,
                    createdEventId = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now,
                )
                snapshot = snapshot.copy(requests = snapshot.requests + request)
                ApiResult.Success(request)
            }
        }
    }

    override suspend fun acceptRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        transition(key, requestId, expectedVersion, setOf(RequestState.PENDING), RequestState.ACCEPTED)

    override suspend fun rejectRequest(key: String, requestId: String, expectedVersion: Int, reason: RejectReason): ApiResult<AssistanceRequest> =
        transition(key, requestId, expectedVersion, setOf(RequestState.PENDING), RequestState.REJECTED, reason.name)

    override suspend fun cancelRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        transition(key, requestId, expectedVersion, setOf(RequestState.PENDING, RequestState.ACCEPTED), RequestState.CANCELLED)

    override suspend fun completeRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        transition(key, requestId, expectedVersion, setOf(RequestState.ACCEPTED), RequestState.COMPLETED)

    override suspend fun advanceTrip(key: String, tripId: String, fromSequence: Int): ApiResult<Trip> = command(key) {
        val trip = snapshot.trip?.takeIf { it.id == tripId }
        when {
            trip == null -> notFound()
            trip.state != TripState.ACTIVE -> ApiResult.Conflict("TRIP_NOT_ACTIVE", "The trip has ended", trip)
            fromSequence != trip.lastPassedSequence ||
                fromSequence >= snapshot.stopVisits.size -> ApiResult.Conflict("VERSION_CONFLICT", "The trip has moved", trip)
            else -> {
                val passed = fromSequence + 1
                val moved = trip.copy(lastPassedSequence = passed)
                snapshot = snapshot.copy(
                    trip = moved,
                    requests = snapshot.requests.map {
                        if (it.sequence == passed && it.state == RequestState.PENDING) close(it, RequestState.EXPIRED, "STOP_PASSED") else it
                    },
                )
                ApiResult.Success(moved)
            }
        }
    }

    override suspend fun endTrip(key: String, tripId: String): ApiResult<Trip> = command(key) {
        val trip = snapshot.trip?.takeIf { it.id == tripId }
        when {
            trip == null -> notFound()
            trip.state != TripState.ACTIVE -> ApiResult.Conflict("TRIP_NOT_ACTIVE", "The trip has ended", trip)
            else -> {
                val ended = trip.copy(state = TripState.ENDED)
                snapshot = snapshot.copy(
                    trip = ended,
                    requests = snapshot.requests.map { if (it.isOpen) close(it, RequestState.EXPIRED, "TRIP_ENDED") else it },
                )
                ApiResult.Success(ended)
            }
        }
    }

    override suspend fun resetDemo(key: String): ApiResult<ResetResult> = command(key) {
        snapshot = load().copy(requests = emptyList())
        ApiResult.Success(ResetResult(snapshot.trip!!, snapshot.stopVisits))
    }

    private suspend fun transition(
        key: String,
        requestId: String,
        expectedVersion: Int,
        from: Set<RequestState>,
        to: RequestState,
        reason: String? = null,
    ): ApiResult<AssistanceRequest> = command(key) {
        val current = snapshot.requests.find { it.id == requestId }
        when {
            current == null -> notFound()
            snapshot.trip?.state != TripState.ACTIVE -> ApiResult.Conflict("TRIP_NOT_ACTIVE", "The trip has ended", current)
            current.version != expectedVersion -> ApiResult.Conflict("VERSION_CONFLICT", "The request has changed", current)
            current.state !in from ->
                ApiResult.Conflict("INVALID_TRANSITION", "A ${current.state} request cannot change to $to", current)
            else -> {
                val changed = close(current, to, reason)
                snapshot = snapshot.copy(requests = snapshot.requests.map { if (it.id == requestId) changed else it })
                ApiResult.Success(changed)
            }
        }
    }

    /** Runs a command once per key and returns the stored answer when the key repeats. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> command(key: String, run: () -> ApiResult<T>): ApiResult<T> = mutex.withLock {
        answers[key]?.let { return@withLock it as ApiResult<T> }
        val result = run()
        if (result is ApiResult.Success) answers[key] = result
        result
    }

    private fun close(request: AssistanceRequest, state: RequestState, reason: String?) =
        request.copy(state = state, closeReason = reason, version = request.version + 1, updatedAt = now())

    private fun notFound() = ApiResult.Failure("NOT_FOUND: not in the sample data", retryable = false, httpStatus = 404)

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
