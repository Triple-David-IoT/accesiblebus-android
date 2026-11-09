package dte.masteriot.accessiblebus.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicInteger
import android.util.Log
data class SensorSnapshot(
    val lightLux: Float? = null,
    val proximity: Float? = null,
    val lightTimestampNanos: Long? = null,
    val proximityTimestampNanos: Long? = null,

    val lightRegistered: Boolean = false,
    val proximityRegistered: Boolean = false
)

class SensorDataSource(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE)
                as SensorManager

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val hasLightSensor: Boolean
        get() = lightSensor != null

    val hasProximitySensor: Boolean
        get() = proximitySensor != null

    /*
     * Instrumentación para la prueba de cierre.
     *
     * Cuenta observaciones activas, no el número de sensores físicos.
     * Cada observación usa un listener que puede estar registrado
     * para uno o para ambos sensores.
     */
    private val activeListenerCount = AtomicInteger(0)

    companion object {
        private const val TAG = "C08"
    }

    internal fun activeListenersForTest(): Int =
        activeListenerCount.get()


    fun logSensorAvailability() {
        Log.i(TAG, "----- Disponibilidad de sensores -----")

        Log.i(
            TAG,
            "Sensor de luz: ${lightSensor != null}" +
                    if (lightSensor != null) {
                        " | ${lightSensor.name}"
                    } else {
                        " | INEXISTENTE"
                    }
        )

        Log.i(
            TAG,
            "Sensor de proximidad: ${proximitySensor != null}" +
                    if (proximitySensor != null) {
                        " | ${proximitySensor.name}" +
                                " | maximumRange=${proximitySensor.maximumRange}"
                    } else {
                        " | INEXISTENTE"
                    }
        )
    }
    fun observeReadings(): Flow<SensorSnapshot> = callbackFlow {
        val registrationTimeNanos = SystemClock.elapsedRealtimeNanos()

        var current = SensorSnapshot()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Ignorar eventos anteriores al inicio de esta observación.
                if (event.timestamp < registrationTimeNanos) return
                if (event.values.isEmpty()) return

                current = when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        val lux = event.values[0]

                        Log.d(
                            TAG,
                            "Lectura de luz: $lux lux"
                        )

                        current.copy(
                            lightLux = lux,
                            lightTimestampNanos = event.timestamp
                        )
                    }

                    Sensor.TYPE_PROXIMITY -> {
                        val proximity = event.values[0]

                        val proximityState = when (proximity) {
                            0f -> "CERCA"
                            5f -> "LEJOS"
                            else -> "VALOR INTERMEDIO"
                        }

                        Log.d(
                            TAG,
                            "Lectura de proximidad: $proximity ($proximityState)"
                        )

                        current.copy(
                            proximity = proximity,
                            proximityTimestampNanos = event.timestamp
                        )
                    }

                    else -> current
                }

                trySend(current)
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int
            ) = Unit
        }

        val lightRegistered = lightSensor?.let {
            sensorManager.registerListener(
                listener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } ?: false

        val proximityRegistered = proximitySensor?.let {
            sensorManager.registerListener(
                listener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } ?: false

        current = current.copy(
            lightRegistered = lightRegistered,
            proximityRegistered = proximityRegistered
        )

        val anyRegistered = lightRegistered || proximityRegistered

        Log.i(
            TAG,
            "Registro de listeners: " +
                    "luz=$lightRegistered, " +
                    "proximidad=$proximityRegistered"
        )

        if (anyRegistered) {
            activeListenerCount.incrementAndGet()
        }

        trySend(current)

        awaitClose {
            Log.i(TAG, "Liberando listeners...")

            sensorManager.unregisterListener(listener)

            if (anyRegistered) {
                activeListenerCount.decrementAndGet()
            }

            Log.i(
                TAG,
                "Listeners activos tras liberar: ${activeListenerCount.get()}"
            )
        }
    }

    /**
     * Calibración confirmada para este dispositivo:
     * 0 = cerca, 5 = lejos.
     *
     * Se usa una comparación explícita con el máximo del sensor como
     * protección si el valor reportado no es exactamente 0 o 5.
     */
    fun isNear(proximityValue: Float): Boolean =
        proximityValue < (proximitySensor?.maximumRange ?: 5f)
}