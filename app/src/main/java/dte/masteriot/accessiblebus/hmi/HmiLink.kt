package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.data.EventFeed
import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.mqtt.ConnectionState

class HmiLink(
    broker: String,
    private val terminalId: String,
    private val vehicleId: String,
    private val scope: CoroutineScope,
) {
    private val presenceTopic = "vehicles/$vehicleId/presence"
    private val client = BusMqttClient(
        broker, terminalId, scope,
        will = BusMqttClient.WillSpec(presenceTopic, presence("offline", withDate = false), retain = true),
    )

    /** Runs after every reconnection, once the presence is republished. */
    var afterReconnect: (suspend () -> Unit)? = null

    private val connected: StateFlow<Boolean> =
        client.state.map { it is ConnectionState.Connected }.stateIn(scope, SharingStarted.Eagerly, false)

    // The events feed and the ramp prototype share one connection: connect only once.
    private val connectLock = Mutex()
    private suspend fun ensureConnected(): Boolean = connectLock.withLock { connected.value || client.connect() }

    val feed: EventFeed = object : EventFeed {
        override val connected: StateFlow<Boolean> = this@HmiLink.connected

        private var subscribed = false

        // The repository's onReconnected is ignored on purpose: afterReconnect runs its /sync.
        override suspend fun start(topic: String, onMessage: (String) -> Unit, onReconnected: () -> Unit): Boolean {
            client.onReconnected = { scope.launch { publishOnline(); afterReconnect?.invoke() } }
            if (!ensureConnected()) return false
            publishOnline()
            if (!subscribed) subscribed = client.subscribe(topic) { _, payload -> onMessage(payload) }
            return subscribed
        }
    }

    suspend fun publishOnline() = client.publish(presenceTopic, presence("online", withDate = true), retain = true)

    suspend fun sendReceipt(request: AssistanceRequest): Boolean = client.publish(
        "terminals/$terminalId/receipts",
        """{"schemaVersion":3,"eventId":"${request.createdEventId}","requestId":"${request.id}","terminalId":"$terminalId"}""",
    )

    // ---- Physical ramp node (K2 proposal, see PhysicalRamp.kt) ----

    private val rampTelemetryTopic = "telemetry/vehicles/$vehicleId/ramp"
    private val rampActuatorTopic = "devices/$RAMP_DEVICE_ID/ramps/$RAMP_NODE_ID"
    private val rampCommandTopic = "commands/vehicles/$vehicleId/ramp"
    private val _rampReading = MutableStateFlow<RampReading?>(null)
    private val _rampActuator = MutableStateFlow<RampActuator?>(null)
    val rampReading: StateFlow<RampReading?> = _rampReading
    val rampActuator: StateFlow<RampActuator?> = _rampActuator
    private var rampSubscribed = false

    /** Connects if needed and subscribes to the ramp node. Returns false while the broker is unreachable. */
    suspend fun startRamp(): Boolean {
        if (!ensureConnected()) return false
        if (!rampSubscribed) {
            rampSubscribed = client.subscribe(rampTelemetryTopic) { _, payload ->
                PhysicalRamp.parseTelemetry(payload, vehicleId, SystemClock.elapsedRealtime())
                    ?.let { _rampReading.value = it }
            } && client.subscribe(rampActuatorTopic) { _, payload ->
                PhysicalRamp.parseActuator(payload)?.let { _rampActuator.value = it }
            }
        }
        return rampSubscribed
    }

    suspend fun sendRampCommand(action: RampAction): Boolean =
        client.publish(rampCommandTopic, PhysicalRamp.commandPayload(action, UUID.randomUUID().toString()))

    /** Explicit offline on a clean exit; the Will covers the dirty ones. */
    suspend fun close() {
        client.publish(presenceTopic, presence("offline", withDate = false), retain = true)
        client.close()
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun presence(status: String, withDate: Boolean): String =
        if (withDate) """{"schemaVersion":3,"status":"$status","terminalId":"$terminalId","reportedAt":"${nowIso()}"}"""
        else """{"schemaVersion":3,"status":"$status","terminalId":"$terminalId"}"""

    private companion object {
        // Identity of the physical node in Rampa_Fisica/firmware (config.h).
        const val RAMP_DEVICE_ID = "ramp-esp32-01"
        const val RAMP_NODE_ID = "ramp-phys-01"
    }
}