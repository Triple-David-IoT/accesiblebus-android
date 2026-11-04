package dte.masteriot.accessiblebus.core.mqtt

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.meshtastic.mqtt.*
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory

class BusMqttClient(
    private val broker: String,
    clientId: String,
    private val scope: CoroutineScope,
    will: WillSpec? = null,
) {
    data class WillSpec(val topic: String, val payload: String, val retain: Boolean)

    private val client = MqttClient(clientId) {
        transportFactory = TcpTransportFactory()
        protocolVersion = MqttProtocolVersion.V3_1_1
        autoReconnect = true
        keepAliveSeconds = 15
        if (will != null) {
            will {
                topic = will.topic
                payload(will.payload.encodeToByteArray())   // ByteArray: no activa payloadFormatIndicator
                qos = QoS.AT_LEAST_ONCE
                retain = will.retain
            }
        }
    }

    val state = client.connectionState
    var onReconnected: (() -> Unit)? = null

    init {
        scope.launch {
            var wasConnected = false
            var everConnected = false
            state.collect { s ->
                val connected = s is ConnectionState.Connected
                if (connected && !wasConnected) {
                    if (everConnected) onReconnected?.invoke()
                    everConnected = true
                }
                wasConnected = connected
            }
        }
    }

    suspend fun connect(): Boolean = try {
        client.connect(MqttEndpoint.parse(broker)); true
    } catch (e: Exception) { Log.e(TAG, "connect: ${e.message}"); false }

    suspend fun subscribe(topic: String, handler: (String, String) -> Unit) {
        try {
            client.subscribe(topic, QoS.AT_LEAST_ONCE)
            scope.launch { client.messagesMatching(topic).collect { handler(it.topic, it.payloadAsString()) } }
        } catch (e: Exception) { Log.e(TAG, "subscribe $topic: ${e.message}") }
    }

    suspend fun publish(topic: String, payload: String, retain: Boolean = false): Boolean = try {
        client.publish(MqttMessage(topic = topic, payload = ByteString(payload.encodeToByteArray()),
            qos = QoS.AT_LEAST_ONCE, retain = retain)); true
    } catch (e: Exception) { Log.e(TAG, "publish $topic: ${e.message}"); false }

    suspend fun close() = try { client.close() } catch (e: Exception) { }

    private companion object { const val TAG = "BusMqttClient" }
}