package dte.masteriot.accessiblebus.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

// Shared app models (K5). They mirror the K1 schemas in contracts/openapi.yaml and the
// K2 event envelope in contracts/mqtt.md, field for field.

/** One JSON configuration for REST and MQTT, tolerant to fields added by later versions. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
enum class TripState { ACTIVE, ENDED }

@Serializable
enum class RequestState { PENDING, ACCEPTED, REJECTED, CANCELLED, COMPLETED, EXPIRED }

@Serializable
enum class AssistanceType { RAMP_BOARDING }

@Serializable
enum class RejectReason { RAMP_UNAVAILABLE, UNSAFE_STOP, OTHER }

@Serializable
data class Trip(
    val id: String,
    val vehicleId: String,
    val driverSubject: String,
    val state: TripState,
    val lastPassedSequence: Int,
)

@Serializable
data class StopVisit(
    val id: String,
    val tripId: String,
    val sequence: Int,
    val physicalStopCode: String,
)

@Serializable
data class AssistanceRequest(
    val id: String,
    val tripId: String,
    val vehicleId: String,
    val ownerSubject: String,
    val stopVisitId: String,
    val sequence: Int,
    val physicalStopCode: String,
    val assistanceType: AssistanceType,
    val state: RequestState,
    val version: Int,
    /** True once the assigned HMI recorded its receipt. It never means a person read it. */
    val received: Boolean,
    val createdEventId: String,
    /** A RejectReason when REJECTED, STOP_PASSED or TRIP_ENDED when EXPIRED, otherwise null. */
    val closeReason: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val isOpen: Boolean get() = state == RequestState.PENDING || state == RequestState.ACCEPTED
}

/** Answer of GET /sync: the current trip (or the last ended one), its visits and the requests. */
@Serializable
data class Snapshot(
    val trip: Trip? = null,
    val stopVisits: List<StopVisit> = emptyList(),
    val requests: List<AssistanceRequest> = emptyList(),
)

@Serializable
data class ResetResult(
    val trip: Trip,
    val stopVisits: List<StopVisit>,
)

/**
 * MQTT event envelope (K2). The payload depends on [eventType]: a full AssistanceRequest for
 * request.created and the transitions, a Trip for trip.progressed and trip.ended, and
 * {"received": true} for request.receipt_recorded. Read it with [requestPayload] or [tripPayload].
 */
@Serializable
data class Event(
    val schemaVersion: Int,
    val eventId: String,
    val eventType: String,
    val tripId: String,
    val requestId: String? = null,
    val version: Int? = null,
    val payload: JsonElement,
) {
    fun requestPayload(): AssistanceRequest = ApiJson.decodeFromJsonElement(payload)

    fun tripPayload(): Trip = ApiJson.decodeFromJsonElement(payload)
}

// Request bodies.

// No default values in bodies: kotlinx.serialization leaves defaults out of the JSON.
@Serializable
data class CreateRequestBody(val stopVisitId: String, val assistanceType: AssistanceType)

@Serializable
data class VersionBody(val expectedVersion: Int)

@Serializable
data class RejectBody(val expectedVersion: Int, val reason: RejectReason)

@Serializable
data class AdvanceBody(val fromSequence: Int)

/** Error body of 401, 403, 404 and 409. currentResource is a request or a trip, or absent. */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val currentResource: JsonElement? = null,
)

/**
 * Result of every API call. A conflict carries the current resource when the server sends it,
 * so the caller can apply it without another call. A failure says whether the same command,
 * with the same idempotency key, can be sent again.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Conflict<T>(val code: String, val message: String, val current: T?) : ApiResult<T>

    data class Failure(val message: String, val retryable: Boolean, val httpStatus: Int? = null) :
        ApiResult<Nothing>
}
