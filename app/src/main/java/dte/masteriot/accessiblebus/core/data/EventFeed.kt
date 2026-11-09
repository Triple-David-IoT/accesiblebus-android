package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.mqtt.ConnectionState

/** Source of the K2 events for one topic. Tests replace it with a fake. */
interface EventFeed {
    val connected: StateFlow<Boolean>

    /**
     * Connects and subscribes to [topic]. [onMessage] receives each message text and
     * [onReconnected] runs after every reconnection. Returns false if the first connect failed.
     */
    suspend fun start(topic: String, onMessage: (String) -> Unit, onReconnected: () -> Unit): Boolean
}

/**
 * EventFeed over BusMqttClient (B08). MQTTastic reconnects and subscribes again by itself
 * (PV-MQTT, docs/mqttastic.md), so a reconnection only needs the /sync that the repository runs.
 */
class MqttEventFeed(private val client: BusMqttClient, scope: CoroutineScope) : EventFeed {

    override val connected: StateFlow<Boolean> =
        client.state.map { it is ConnectionState.Connected }.stateIn(scope, SharingStarted.Eagerly, false)

    private var subscribed = false

    override suspend fun start(topic: String, onMessage: (String) -> Unit, onReconnected: () -> Unit): Boolean {
        client.onReconnected = onReconnected
        if (!connected.value && !client.connect()) return false
        if (!subscribed) subscribed = client.subscribe(topic) { _, payload -> onMessage(payload) }
        return subscribed
    }
}
