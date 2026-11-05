package dte.masteriot.accessiblebus.sensors

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log

class SensorTestActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        logAvailableSensors()
    }

    override fun onResume() {
        super.onResume()
        registerSensorListeners()
    }

    override fun onPause() {
        unregisterSensorListeners()
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> logLightMeasurement(value)
            Sensor.TYPE_PROXIMITY -> logProximityMeasurement(value)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name}, accuracy: $accuracy")
    }

    private fun registerSensorListeners() {
        lightSensor?.let {
            val registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Light sensor registration: $registered")
        } ?: Log.w(TAG, "Light sensor unavailable")

        proximitySensor?.let {
            val registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Proximity sensor registration: $registered")
        } ?: Log.w(TAG, "Proximity sensor unavailable")
    }

    private fun unregisterSensorListeners() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensor listeners unregistered")
    }

    private fun logAvailableSensors() {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)

        Log.d(TAG, "Available sensors: ${sensors.size}")

        sensors.forEach { sensor ->
            Log.d(
                TAG,
                "Sensor: ${sensor.name}, type: ${sensor.type}, vendor: ${sensor.vendor}"
            )
        }

        logSensorDetails(lightSensor)
        logSensorDetails(proximitySensor)
    }

    private fun logSensorDetails(sensor: Sensor?) {
        if (sensor == null) {
            Log.w(TAG, "Sensor unavailable")
            return
        }

        Log.d(
            TAG,
            "Name: ${sensor.name}, type: ${sensor.type}, " +
                    "vendor: ${sensor.vendor}, maxRange: ${sensor.maximumRange}, " +
                    "resolution: ${sensor.resolution}"
        )
    }

    private fun logLightMeasurement(value: Float) {
        Log.d(TAG, "Light measurement: $value lux")
    }

    private fun logProximityMeasurement(value: Float) {
        Log.d(TAG, "Proximity measurement: $value")
    }

    companion object {
        private const val TAG = "C02_SENSORES"
    }
}
