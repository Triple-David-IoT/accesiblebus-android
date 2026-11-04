package dte.masteriot.accessiblebus.data

import android.content.Context
import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Repository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val fileSource = FileDataSource(context)
    private val mqtt = BusMqttClient(
        broker = "tcp://10.0.2.2:1883",
        clientId = "AccessibleBusApp-${System.currentTimeMillis()}",
        scope = scope
    )
    private val json = Json { ignoreUnknownKeys = true }

    val mqttConnection = mqtt.state

    suspend fun connectMqtt() = mqtt.connect()

    fun arrivals(stopId: String): Flow<StopArrivals> = callbackFlow {
        val topic = "bus/stops/$stopId/arrivals"
        mqtt.subscribe(topic) { _, payload ->
            try {
                trySend(json.decodeFromString<StopArrivals>(payload))
            } catch (_: Exception) { }
        }
        awaitClose { }
    }

    suspend fun sendRequest(r: AssistanceRequest): Boolean =
        mqtt.publish("bus/requests/${r.requestId}", json.encodeToString(r))

    fun requestStatus(requestId: String): Flow<AssistanceRequest> = callbackFlow {
        val topic = "bus/requests/$requestId/status"
        mqtt.subscribe(topic) { _, payload ->
            try {
                trySend(json.decodeFromString<AssistanceRequest>(payload))
            } catch (_: Exception) { }
        }
        awaitClose { }
    }

    suspend fun close() = mqtt.close()
}
