package dte.masteriot.accessiblebus.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class SensorReading (
    val lightLux: Float? = null,
    val proximity: Float? = null,
    val lightTimestamp: Long? = null,
    val proximityTimestamp: Long? = null
)

class SensorDataSource(
    context: Context
) {

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

    val proximityMaximumRange: Float
        get() = proximitySensor?.maximumRange ?: 0f

    fun isNear(value: Float): Boolean {

        if (proximitySensor == null) {
            return false
        }

        return value < proximityMaximumRange
    }

    fun observeReadings(): Flow<SensorReading> = callbackFlow {

        var lightLux: Float? = null
        var proximity: Float? = null

        var lightTimestamp: Long? = null
        var proximityTimestamp: Long? = null

        val registrationTime = System.nanoTime()

        val listener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {

                // Ignorar eventos anteriores al registro.
                if (event.timestamp < registrationTime) {
                    return
                }

                when (event.sensor.type) {

                    Sensor.TYPE_LIGHT -> {
                        lightLux = event.values[0]
                        lightTimestamp = event.timestamp
                    }

                    Sensor.TYPE_PROXIMITY -> {
                        proximity = event.values[0]
                        proximityTimestamp = event.timestamp
                    }
                }

                trySend(
                    SensorReading(
                        lightLux = lightLux,
                        proximity = proximity,
                        lightTimestamp = lightTimestamp,
                        proximityTimestamp = proximityTimestamp
                    )
                )
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int
            ) {
                // No necesitamos modificar el contexto
                // por un cambio de precisión.
            }
        }

        // Registrar solamente los sensores existentes.
        lightSensor?.let {
            sensorManager.registerListener(
                listener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        proximitySensor?.let {
            sensorManager.registerListener(
                listener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}