package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiJson
import dte.masteriot.accessiblebus.core.net.ApiResult
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import dte.masteriot.accessiblebus.core.net.Event
import dte.masteriot.accessiblebus.core.net.RejectReason
import dte.masteriot.accessiblebus.core.net.Snapshot
import dte.masteriot.accessiblebus.core.net.Trip
import dte.masteriot.accessiblebus.core.net.TripState
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Requests and trip state for the screens (K5). Three inputs change [snapshot], always with the
 * same rules: command answers, GET /sync and the MQTT events of [eventsTopic]. The higher
 * version of a request wins, received stays true, and a trip only moves forward.
 *
 * Commands go through [ApiSource] with a key saved in [keys] before sending. Every decision on
 * a request (accepted, rejected, completed, expired) reaches [announcer] once, whichever input
 * brought it, because [announced] remembers "requestId:version" on disk.
 *
 * [syncScope] and [vehicleId] are those of GET /sync: "mine" for the passenger, "vehicle" with
 * the bus for the driver. [scope] runs the /sync that follows a reconnection or a new trip.
 */
class AssistanceRepository(
    private val api: ApiSource,
    private val keys: CommandKeys,
    private val syncScope: String = "mine",
    private val vehicleId: String? = null,
    private val events: EventFeed? = null,
    private val eventsTopic: String? = null,
    private val announcer: DecisionAnnouncer = DecisionAnnouncer {},
    private val announced: AnnouncedDecisions = MemoryAnnouncedDecisions(),
    private val scope: CoroutineScope? = null,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    // Keys left from a previous run have no answer yet: show them as unconfirmed.
    private val _commands = MutableStateFlow<Map<String, CommandState>>(
        keys.all().associateWith { CommandState.Unconfirmed("Sin confirmar antes de cerrar la app") },
    )
    val commands: StateFlow<Map<String, CommandState>> = _commands.asStateFlow()

    /** True while the MQTT connection is up; always false without events, as with the sample API. */
    val live: StateFlow<Boolean> = events?.connected ?: MutableStateFlow(false)

    // QoS 1 can deliver an event twice: each eventId is processed once.
    private val seenEvents = LinkedHashSet<String>()
    private val started = AtomicBoolean(false)
    private val lock = Any()

    // ---- Reads and events

    suspend fun refresh(): ApiResult<Snapshot> {
        val result = api.sync(syncScope, vehicleId)
        if (result is ApiResult.Success) change { merge(it, result.value) }
        return result
    }

    /**
     * Connects to the events once per process. Retries the first connection with a growing wait;
     * after that MQTTastic reconnects by itself and every reconnection runs a /sync.
     */
    fun startEvents() {
        val feed = events ?: return
        val topic = eventsTopic ?: return
        val runner = scope ?: return
        if (!started.compareAndSet(false, true)) return
        runner.launch {
            var wait = 2_000L
            while (!feed.start(topic, ::onEvent) { runner.launch { refresh() } }) {
                delay(wait)
                wait = minOf(wait * 2, 30_000L)
            }
            refresh()
        }
    }

    /** Applies one K2 event. Unknown, repeated or malformed messages are ignored. */
    fun onEvent(text: String) {
        val event = runCatching { ApiJson.decodeFromString(Event.serializer(), text) }.getOrNull() ?: return
        synchronized(lock) {
            if (!seenEvents.add(event.eventId)) return
            if (seenEvents.size > 500) seenEvents.remove(seenEvents.first())
        }
        val type = event.eventType
        when {
            type == "request.receipt_recorded" -> change { snapshot ->
                snapshot.copy(requests = snapshot.requests.map { if (it.id == event.requestId) it.copy(received = true) else it })
            }
            type.startsWith("request.") -> runCatching { event.requestPayload() }.getOrNull()?.let(::applyRequest)
            type.startsWith("trip.") -> runCatching { event.tripPayload() }.getOrNull()?.let { trip ->
                // Another trip means new visits: /sync brings them.
                if (_snapshot.value.trip?.id == trip.id) applyTrip(trip) else scope?.launch { refresh() }
            }
        }
    }

    // ---- Commands

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

    // ---- Snapshot changes

    private fun applyRequest(incoming: AssistanceRequest) = change { it.copy(requests = mergeRequests(it.requests, listOf(incoming))) }

    private fun applyTrip(incoming: Trip) = change { current ->
        val trip = current.trip
        if (trip == null || trip.id != incoming.id) current else current.copy(trip = mergeTrip(trip, incoming))
    }

    /** The only way the snapshot changes, so every new decision is seen and announced once. */
    private fun change(transform: (Snapshot) -> Snapshot) {
        val decisions = synchronized(lock) {
            val before = _snapshot.value
            val after = transform(before)
            _snapshot.value = after
            newDecisions(before, after)
        }
        decisions.filter { announced.add("${it.requestId}:${it.version}") }.forEach(announcer::announce)
    }

    companion object {
        /**
         * Combines what the app has with a /sync answer. Another trip replaces everything, visits
         * included; the same trip keeps the furthest progress and the newest version of each request.
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

        /**
         * Requests of the current trip that reached a decision state in a version the app did not
         * have before. Requests of the previous trip, which /sync also returns, are not announced.
         */
        fun newDecisions(before: Snapshot, after: Snapshot): List<DecisionEvent> {
            val old = before.requests.associateBy { it.id }
            val trip = after.trip?.id
            return after.requests
                .filter { it.tripId == trip && it.state in DECISION_STATES && (old[it.id]?.version ?: 0) < it.version }
                .map { DecisionEvent(it.id, it.version, it.state, it.closeReason) }
        }
    }
}

class MemoryAnnouncedDecisions : AnnouncedDecisions {
    private val keys = mutableSetOf<String>()

    @Synchronized
    override fun add(key: String) = keys.add(key)
}
