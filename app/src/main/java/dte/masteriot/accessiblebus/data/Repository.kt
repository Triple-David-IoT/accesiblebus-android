
package dte.masteriot.accessiblebus.data

import android.content.Context
import dte.masteriot.accessiblebus.core.config.AppConfig
import dte.masteriot.accessiblebus.core.config.ConnectionProfile
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
    private val appConfigRepository: AppConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob()
    )
) {
    private val fileSource = FileDataSource(context)

    private var mqtt: BusMqttClient? = null

    private val json = Json {
        ignoreUnknownKeys = true
    }

    val mqttConnection
        get() = mqtt?.state

    suspend fun connectMqtt(profile: ConnectionProfile): Boolean {
        val config = appConfigRepository.getConfig(profile)

        if (config.mqttHost.isBlank()) {
            return false
        }

        mqtt?.close()

        val broker = "tcp://${config.mqttHost}:${config.mqttPort}"

        val newMqtt = BusMqttClient(
            broker = broker,
            clientId = config.clientId,
            scope = scope
        )

        mqtt = newMqtt

        return newMqtt.connect()
    }

    fun arrivals(stopId: String): Flow<StopArrivals> = callbackFlow {
        val currentMqtt = mqtt
            ?: throw IllegalStateException("MQTT client is not connected")

        val topic = "bus/stops/$stopId/arrivals"

        currentMqtt.subscribe(topic) { _, payload ->
            try {
                trySend(json.decodeFromString<StopArrivals>(payload))
            } catch (_: Exception) {
            }
        }

        awaitClose { }
    }

    suspend fun sendRequest(r: AssistanceRequest): Boolean {
        val currentMqtt = mqtt ?: return false

        return currentMqtt.publish(
            "bus/requests/${r.requestId}",
            json.encodeToString(r)
        )
    }

    fun requestStatus(requestId: String): Flow<AssistanceRequest> =
        callbackFlow {
            val currentMqtt = mqtt
                ?: throw IllegalStateException("MQTT client is not connected")

            val topic = "bus/requests/$requestId/status"

            currentMqtt.subscribe(topic) { _, payload ->
                try {
                    trySend(
                        json.decodeFromString<AssistanceRequest>(payload)
                    )
                } catch (_: Exception) {
                }
            }

            awaitClose { }
        }

    suspend fun close() {
        mqtt?.close()
        mqtt = null
    }
}
