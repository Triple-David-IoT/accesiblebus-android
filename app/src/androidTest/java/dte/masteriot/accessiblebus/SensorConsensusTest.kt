package dte.masteriot.accessiblebus

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dte.masteriot.accessiblebus.sensors.SensorDataSource
import dte.masteriot.accessiblebus.sensors.ContextEstimator
import dte.masteriot.accessiblebus.sensors.PhoneContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensorConsensusTest {

    companion object {
        private const val TAG = "C08_TEST"
    }

    @Test
    fun testEstimateAndReleaseListeners(): Unit = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        Log.i(TAG, "========== INICIO TEST C08 ==========")

        val sensorDataSource = SensorDataSource(context)
        val estimator = ContextEstimator(sensorDataSource)

        // Mostrar qué sensores existen en el dispositivo.
        sensorDataSource.logSensorAvailability()

        // Ejecutar la estimación real.
        val result: PhoneContext = estimator.estimate()

        Log.i(TAG, "Resultado de estimate(): $result")

        // La función debe terminar y devolver un contexto.
        assertNotNull(result)

        // Tras estimate(), no debe quedar ninguna observación activa.
        val activeListeners =
            sensorDataSource.activeListenersForTest()

        Log.i(
            TAG,
            "Listeners activos después de estimate(): $activeListeners"
        )

        assertEquals(
            "Quedan listeners registrados tras estimate()",
            0,
            activeListeners
        )

        Log.i(TAG, "========== FIN TEST C08 ==========")
    }
}