package dte.masteriot.accessiblebus.sensors

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensorConsensusTest {

    companion object {
        private const val TAG = "SensorsTest"
    }

    @Test
    fun testEstimateAndReleaseListeners(): Unit = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        Log.i(TAG, "========== START sensor test ==========")

        val sensorDataSource = SensorDataSource(context)
        val estimator = ContextEstimator(sensorDataSource)

        // Log which sensors the device has.
        sensorDataSource.logSensorAvailability()

        // Run the real estimation.
        val result: PhoneContext = estimator.estimate()

        Log.i(TAG, "estimate() result: $result")

        // The function must finish and return a context.
        assertNotNull(result)

        // After estimate(), no listener may remain registered.
        val activeListeners =
            sensorDataSource.activeListenersForTest()

        Log.i(
            TAG,
            "Active listeners after estimate(): $activeListeners"
        )

        assertEquals(
            "Listeners still registered after estimate()",
            0,
            activeListeners
        )

        Log.i(TAG, "========== END sensor test ==========")
    }
}