package dte.masteriot.accessiblebus

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dte.masteriot.accessiblebus.navigation.AppNav
import dte.masteriot.accessiblebus.ui.theme.AppTheme

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager

    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    private lateinit var lightText: TextView
    private lateinit var proximityText: TextView

    companion object {
        private const val TAG = "C02_SENSORES"
    }

    private val container: AppContainer
        get() = (application as AccessibleBusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = container.repository

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        // Interfaz sencilla para mostrar las mediciones si se usa Views
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val titleText = TextView(this).apply {
            text = "Mediciones de sensores"
            textSize = 24f
        }

        lightText = TextView(this).apply {
            textSize = 18f
            text = "Luz: esperando medición..."
        }

        proximityText = TextView(this).apply {
            textSize = 18f
            text = "Proximidad: esperando medición..."
        }

        layout.addView(titleText)
        layout.addView(lightText)
        layout.addView(proximityText)

        // Registrar los sensores disponibles
        Log.d(TAG, "Sensores disponibles: ${sensorManager.getSensorList(Sensor.TYPE_ALL).size}")
        Log.d(TAG, "Sensor de luz: ${lightSensor?.name ?: "No disponible"}")
        Log.d(TAG, "Sensor de proximidad: ${proximitySensor?.name ?: "No disponible"}")

        setContent {
            AppTheme(useHighContrast = false) {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "onResume(): Activity en primer plano")

        lightSensor?.let {
            val registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            Log.d(TAG, "Registro sensor de luz: $registered")
        }

        proximitySensor?.let {
            val registered = sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )

            Log.d(TAG, "Registro sensor de proximidad: $registered")
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause(): Activity pausada")

        sensorManager.unregisterListener(this)

        Log.d(TAG, "Listener desregistrado")

        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop(): Activity deja de ser visible")
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart(): Activity vuelve a ser visible")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0]

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                if (::lightText.isInitialized) {
                    lightText.text = "Luz ambiental: $value lux"
                }
                Log.d(TAG, "Medición de luz: $value lux")
            }

            Sensor.TYPE_PROXIMITY -> {
                if (::proximityText.isInitialized) {
                    proximityText.text = "Proximidad: $value cm"
                }
                Log.d(TAG, "Medición de proximidad: $value")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Precisión modificada: ${sensor?.name}, precisión: $accuracy")
    }
}
