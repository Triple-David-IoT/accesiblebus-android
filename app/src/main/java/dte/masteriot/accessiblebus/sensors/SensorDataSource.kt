package dte.masteriot.accessiblebus.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicInteger

data class SensorSnapshot(
    val lightLux: Float? = null,
    val proximity: Float? = null,
    val lightTimestampNanos: Long? = null,
    val proximityTimestampNanos: Long? = null,
    val lightRegistered: Boolean = false,
    val proximityRegistered: Boolean = false
)

class SensorDataSource(context: Context) {

    companion object {
        private const val TAG = "C08"
    }

    private val activeListenerCount = AtomicInteger(0)

    internal fun activeListenersForTest(): Int =
        activeListenerCount.get()
    private val sensorManager =
        context.applicationContext.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val hasLightSensor: Boolean
        get() = lightSensor != null

    val hasProximitySensor: Boolean
        get() = proximitySensor != null

    fun observeReadings(): Flow<SensorSnapshot> = callbackFlow {
        val registrationTimeNanos =
            SystemClock.elapsedRealtimeNanos()

        var current = SensorSnapshot()

        val listener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {
                if (event.timestamp < registrationTimeNanos) return
                if (event.values.isEmpty()) return

                current = when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        current.copy(
                            lightLux = event.values[0],
                            lightTimestampNanos = event.timestamp
                        )
                    }

                    Sensor.TYPE_PROXIMITY -> {
                        current.copy(
                            proximity = event.values[0],
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

        if (anyRegistered) {
            activeListenerCount.incrementAndGet()
        }

        trySend(current)

        awaitClose {
            sensorManager.unregisterListener(listener)

            if (anyRegistered) {
                activeListenerCount.decrementAndGet()
            }
        }

        trySend(current)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    fun isNear(proximityValue: Float): Boolean {
        val maximumRange = proximitySensor?.maximumRange
            ?: return false

        return proximityValue < maximumRange
    }

    fun logSensorAvailability() {
        Log.i(
            TAG,
            "Sensor de luz: ${lightSensor != null}" +
                    (lightSensor?.let { " | ${it.name}" } ?: " | INEXISTENTE")
        )

        Log.i(
            TAG,
            "Sensor de proximidad: ${proximitySensor != null}" +
                    (proximitySensor?.let {
                        " | ${it.name} | maximumRange=${it.maximumRange}"
                    } ?: " | INEXISTENTE")
        )
    }
}