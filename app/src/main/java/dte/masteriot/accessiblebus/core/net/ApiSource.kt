package dte.masteriot.accessiblebus.core.net

/**
 * The eleven K1 operations. Commands take the idempotency key from the caller, which keeps it
 * until it gets an answer, so a retry after a timeout reuses it and the server runs it once.
 *
 * ApiClient talks to the real API; SampleApi answers from assets/samples without a network.
 */
interface ApiSource {
    /** scope "mine" for the passenger; "vehicle" with [vehicleId] for the driver and the operator. */
    suspend fun sync(scope: String, vehicleId: String? = null): ApiResult<Snapshot>

    suspend fun getRequest(requestId: String): ApiResult<AssistanceRequest>

    suspend fun stopVisits(tripId: String): ApiResult<List<StopVisit>>

    suspend fun createRequest(key: String, stopVisitId: String): ApiResult<AssistanceRequest>

    suspend fun acceptRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest>

    suspend fun rejectRequest(
        key: String,
        requestId: String,
        expectedVersion: Int,
        reason: RejectReason,
    ): ApiResult<AssistanceRequest>

    suspend fun cancelRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest>

    suspend fun completeRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest>

    suspend fun advanceTrip(key: String, tripId: String, fromSequence: Int): ApiResult<Trip>

    suspend fun endTrip(key: String, tripId: String): ApiResult<Trip>

    /** Operator only. Ends the active demo trip and starts a new one. */
    suspend fun resetDemo(key: String): ApiResult<ResetResult>
}
