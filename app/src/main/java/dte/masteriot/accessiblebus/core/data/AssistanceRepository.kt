package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiResult
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.RejectReason
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.Trip
import dte.masteriot.accessiblebus.core.net.TripState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the app knows about a command that has not finished cleanly. */
sealed interface CommandState {
    /** Sent and waiting for the answer. Shown as "Pendiente de envío". */
    data object Sending : CommandState

    /** No answer: the key is kept and the same command can be sent again. */
    data class Unconfirmed(val message: String) : CommandState

    /** The server said no. The key is dropped; trying again is a new intention with a new key. */
    data class Refused(val code: String, val message: String) : CommandState
}

/**
 * Ids of the commands. The id names one intention: the same id reuses the same key, so it
 * includes everything the body depends on, such as the version the user saw.
 */
object CommandIds {
    fun create(stopVisitId: String) = "create:$stopVisitId"

    fun request(operation: String, request: AssistanceRequest) = "$operation:${request.id}:${request.version}"

    fun advance(trip: Trip) = "advance:${trip.id}:${trip.lastPassedSequence}"

    fun end(trip: Trip) = "end:${trip.id}"
}

/**
 * Requests and trip state for the screens (K5). Commands go through [ApiSource] with a key saved
 * in [keys] before sending; answers are applied to [snapshot] by version, so an older answer
 * never hides a newer state. A14 adds the MQTT events to the same [snapshot].
 *
 * [syncScope] and [vehicleId] are those of GET /sync: "mine" for the passenger, "vehicle" with
 * the bus for the driver.
 */
class AssistanceRepository(
    private val api: ApiSource,
    private val keys: CommandKeys,
    private val syncScope: String = "mine",
    private val vehicleId: String? = null,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    // Keys left from a previous run have no answer yet: show them as unconfirmed.
    private val _commands = MutableStateFlow<Map<String, CommandState>>(
        keys.all().associateWith { CommandState.Unconfirmed("Sin confirmar antes de cerrar la app") },
    )
    val commands: StateFlow<Map<String, CommandState>> = _commands.asStateFlow()

    suspend fun refresh(): ApiResult<Snapshot> {
        val result = api.sync(syncScope, vehicleId)
        if (result is ApiResult.Success) _snapshot.update { merge(it, result.value) }
        return result
    }

    suspend fun create(stopVisitId: String): ApiResult<AssistanceRequest> =
        send(CommandIds.create(stopVisitId), ::applyRequest) { key -> api.createRequest(key, stopVisitId) }

    suspend fun accept(requestId: String) = onRequest("accept", requestId) { key, r -> api.acceptRequest(key, r.id, r.version) }

    suspend fun reject(requestId: String, reason: RejectReason) =
        onRequest("reject", requestId) { key, r -> api.rejectRequest(key, r.id, r.version, reason) }

    suspend fun cancel(requestId: String) = onRequest("cancel", requestId) { key, r -> api.cancelRequest(key, r.id, r.version) }

    suspend fun complete(requestId: String) = onRequest("complete", requestId) { key, r -> api.completeRequest(key, r.id, r.version) }

    suspend fun advance(): ApiResult<Trip> = onTrip(CommandIds::advance) { key, t -> api.advanceTrip(key, t.id, t.lastPassedSequence) }

    suspend fun end(): ApiResult<Trip> = onTrip(CommandIds::end) { key, t -> api.endTrip(key, t.id) }

    /** Forgets a refused command so the screen stops showing its message. */
    fun dismiss(commandId: String) {
        if (_commands.value[commandId] is CommandState.Refused) _commands.update { it - commandId }
    }

    private suspend fun onRequest(
        operation: String,
        requestId: String,
        call: suspend (String, AssistanceRequest) -> ApiResult<AssistanceRequest>,
    ): ApiResult<AssistanceRequest> {
        val request = _snapshot.value.requests.find { it.id == requestId }
            ?: return ApiResult.Failure("Solicitud desconocida", retryable = false)
        return send(CommandIds.request(operation, request), ::applyRequest) { key -> call(key, request) }
    }

    private suspend fun onTrip(id: (Trip) -> String, call: suspend (String, Trip) -> ApiResult<Trip>): ApiResult<Trip> {
        val trip = _snapshot.value.trip ?: return ApiResult.Failure("No hay viaje", retryable = false)
        return send(id(trip), ::applyTrip) { key -> call(key, trip) }
    }

    private suspend fun <T> send(commandId: String, apply: (T) -> Unit, call: suspend (String) -> ApiResult<T>): ApiResult<T> {
        if (_commands.value[commandId] == CommandState.Sending) {
            return ApiResult.Failure("Ya se está enviando", retryable = true)
        }
        val key = keys.get(commandId) ?: newKey().also { keys.put(commandId, it) }
        setState(commandId, CommandState.Sending)
        val result = try {
            call(key)
        } catch (e: CancellationException) {
            setState(commandId, CommandState.Unconfirmed("Envío interrumpido"))
            throw e
        }
        when (result) {
            is ApiResult.Success -> {
                apply(result.value)
                keys.remove(commandId)
                setState(commandId, null)
            }
            is ApiResult.Conflict -> {
                result.current?.let(apply)
                keys.remove(commandId)
                setState(commandId, CommandState.Refused(result.code, result.message))
            }
            is ApiResult.Failure -> if (result.retryable) {
                setState(commandId, CommandState.Unconfirmed(result.message))
            } else {
                keys.remove(commandId)
                setState(commandId, CommandState.Refused("HTTP_${result.httpStatus ?: 0}", result.message))
            }
        }
        return result
    }

    private fun setState(commandId: String, state: CommandState?) =
        _commands.update { if (state == null) it - commandId else it + (commandId to state) }

    private fun applyRequest(incoming: AssistanceRequest) = _snapshot.update { it.copy(requests = mergeRequests(it.requests, listOf(incoming))) }

    private fun applyTrip(incoming: Trip) = _snapshot.update { current ->
        val trip = current.trip
        if (trip == null || trip.id != incoming.id) current else current.copy(trip = mergeTrip(trip, incoming))
    }

    companion object {
        /**
         * Combines what the app has with a /sync answer. Another trip replaces everything; the same
         * trip keeps the furthest progress and the newest version of each request.
         */
        fun merge(current: Snapshot, incoming: Snapshot): Snapshot {
            val trip = current.trip
            val next = incoming.trip
            if (trip == null || next == null || trip.id != next.id) return incoming
            return Snapshot(
                trip = mergeTrip(trip, next),
                stopVisits = incoming.stopVisits,
                requests = mergeRequests(current.requests, incoming.requests),
            )
        }

        /** Same trip: the larger lastPassedSequence wins and ENDED never goes back to ACTIVE. */
        fun mergeTrip(current: Trip, incoming: Trip): Trip = incoming.copy(
            lastPassedSequence = maxOf(current.lastPassedSequence, incoming.lastPassedSequence),
            state = if (current.state == TripState.ENDED) TripState.ENDED else incoming.state,
        )

        /** The higher version of each request wins; received, once true, stays true. */
        fun mergeRequests(current: List<AssistanceRequest>, incoming: List<AssistanceRequest>): List<AssistanceRequest> {
            val byId = LinkedHashMap<String, AssistanceRequest>()
            current.forEach { byId[it.id] = it }
            incoming.forEach { new ->
                val old = byId[new.id]
                byId[new.id] = when {
                    old == null -> new
                    new.version > old.version -> new.copy(received = new.received || old.received)
                    else -> old.copy(received = old.received || new.received)
                }
            }
            return byId.values.toList()
        }
    }
}
