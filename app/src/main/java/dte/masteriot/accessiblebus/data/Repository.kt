package dte.masteriot.accessiblebus.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class Repository(context: Context) {
    private val fileSource = FileDataSource(context)
    private val mqtt = MqttDataSource()
    private val json = Json { ignoreUnknownKeys = true }

    val mqttConnection = mqtt.connectionState

    suspend fun connectMqtt() = mqtt.connect()

    fun arrivals(stopId: String): Flow<StopArrivals> = flow {
        val topic = "bus/stops/$stopId/arrivals"
        mqtt.subscribe(topic)
        emitAll(
            mqtt.messages(topic).mapNotNull { (_, payload) ->
                try { json.decodeFromString<StopArrivals>(payload) } catch (e: Exception) { null }
            }
        )
    }

    suspend fun sendRequest(r: AssistanceRequest): Boolean =
        mqtt.publish("bus/requests/${r.requestId}", json.encodeToString(r))

    fun requestStatus(requestId: String): Flow<AssistanceRequest> = flow {
        val topic = "bus/requests/$requestId/status"
        mqtt.subscribe(topic)
        emitAll(
            mqtt.messages(topic).mapNotNull { (_, payload) ->
                try { json.decodeFromString<AssistanceRequest>(payload) } catch (e: Exception) { null }
            }
        )
    }

    suspend fun close() = mqtt.close()
}