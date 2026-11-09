package dte.masteriot.accessiblebus.hmi

import dte.masteriot.accessiblebus.core.data.EventFeed
import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import dte.masteriot.accessiblebus.core.net.AssistanceRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.mqtt.ConnectionState

class HmiLink(
    broker: String,
    private val terminalId: String,
    vehicleId: String,
    private val scope: CoroutineScope,
) {
    private val presenceTopic = "vehicles/$vehicleId/presence"
    private val client = BusMqttClient(
        broker, terminalId, scope,
        will = BusMqttClient.WillSpec(presenceTopic, presence("offline", withDate = false), retain = true),
    )

    /** Runs after every reconnection, once the presence is republished. */
    var afterReconnect: (suspend () -> Unit)? = null

    val feed: EventFeed = object : EventFeed {
        override val connected: StateFlow<Boolean> =
            client.state.map { it is ConnectionState.Connected }.stateIn(scope, SharingStarted.Eagerly, false)

        private var subscribed = false

        // The repository's onReconnected is ignored on purpose: afterReconnect runs its /sync.
        override suspend fun start(topic: String, onMessage: (String) -> Unit, onReconnected: () -> Unit): Boolean {
            client.onReconnected = { scope.launch { publishOnline(); afterReconnect?.invoke() } }
            if (!connected.value && !client.connect()) return false
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
}