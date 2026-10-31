package dte.masteriot.accessiblebus.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.messagesMatching
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory

private const val MQTT_TAG = "MqttDataSource"

// 10.0.2.2 = PC anfitrión desde el emulador. En móvil real, la IP LAN del PC con el broker.
//const val DEFAULT_BROKER = "tcp://10.0.2.2:1883"
const val DEFAULT_BROKER = "tcp://localhost:1883"  // por adb reverse tcp:1883 tcp:1883

class MqttDataSource(private val broker: String = DEFAULT_BROKER) {

    private val client = MqttClient("bus-app-" + System.currentTimeMillis()) {
        transportFactory = TcpTransportFactory()
        autoReconnect = true
        keepAliveSeconds = 30
    }

    private val connectMutex = Mutex()
    private val subscribedTopics = mutableSetOf<String>()

    /** Estado de la conexión (StateFlow), para mostrarlo en la UI. */
    val connectionState = client.connectionState

    /** Conecta con el broker. Si falla, lo registra y devuelve false (la app no se cae). */
    suspend fun connect(): Boolean = connectMutex.withLock {
        try {
            client.connect(MqttEndpoint.parse(broker))
            Log.d(MQTT_TAG, "Conectado a $broker")
            true
        } catch (e: Exception) {
            Log.e(MQTT_TAG, "No se pudo conectar a $broker", e)
            false
        }
    }

    /** Suscribe al topic (admite comodines como bus/stops/+/arrivals). */
    suspend fun subscribe(topic: String) {
        try {
            client.subscribe(topic, QoS.AT_LEAST_ONCE)
            subscribedTopics.add(topic)
        } catch (e: Exception) {
            Log.e(MQTT_TAG, "No se pudo suscribir a $topic", e)
        }
    }

    /** Publica un texto (normalmente JSON). Devuelve true si no hubo error. */
    suspend fun publish(topic: String, payload: String): Boolean =
        try {
            client.publish(topic, payload, QoS.AT_LEAST_ONCE)
            true
        } catch (e: Exception) {
            Log.e(MQTT_TAG, "No se pudo publicar en $topic", e)
            false
        }

    /** Mensajes recibidos que encajan con el filtro, como pares (topic, payload). */
    fun messages(filter: String): Flow<Pair<String, String>> =
        client.messagesMatching(filter).map { it.topic to it.payloadAsString() }

    /** Vuelve a suscribir todos los topics. Útil tras una reconexión. */
    suspend fun resubscribeAll() {
        subscribedTopics.toList().forEach { subscribe(it) }
    }

    suspend fun close() {
        try { client.close() } catch (e: Exception) { Log.e(MQTT_TAG, "Error al cerrar", e) }
    }
}