package dte.masteriot.accessiblebus.sensors

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class ContextEstimator(
    private val sensorDataSource: SensorDataSource
) {

    companion object {
        private const val SENSOR_TIMEOUT_MS = 1_500L

        private const val L_OSC = 5f
        private const val L_LUZ = 20f
    }

    suspend fun estimate(): PhoneContext {
        var latestSnapshot = SensorSnapshot()

        val result = withTimeoutOrNull(
            SENSOR_TIMEOUT_MS.milliseconds
        ) {
            sensorDataSource.observeReadings().first { snapshot ->
                latestSnapshot = snapshot
                hasEnoughEvidence(snapshot)
            }
        }

        val snapshot = result ?: latestSnapshot
        val timedOut = result == null

        val missingExpectedReading =
            (snapshot.lightRegistered && snapshot.lightLux == null) ||
                    (snapshot.proximityRegistered && snapshot.proximity == null)

        return when {
            timedOut && missingExpectedReading ->
                PhoneContext.UNKNOWN

            else ->
                decide(snapshot)
        }
    }

    private fun hasEnoughEvidence(
        snapshot: SensorSnapshot
    ): Boolean {
        val lightExpected = snapshot.lightRegistered
        val proximityExpected = snapshot.proximityRegistered

        if (!lightExpected && !proximityExpected) {
            return true
        }

        val lightReady =
            !lightExpected || snapshot.lightLux != null

        val proximityReady =
            !proximityExpected || snapshot.proximity != null

        return lightReady && proximityReady
    }

    private fun decide(
        snapshot: SensorSnapshot
    ): PhoneContext {
        val lightAvailable = snapshot.lightRegistered
        val proximityAvailable = snapshot.proximityRegistered

        val lux = snapshot.lightLux
        val proximity = snapshot.proximity

        val hasLightReading =
            lightAvailable && lux != null

        val hasProximityReading =
            proximityAvailable && proximity != null

        if (!hasLightReading && !hasProximityReading) {
            return PhoneContext.UNKNOWN
        }

        if (hasLightReading && hasProximityReading) {
            return decideWithBoth(
                lux = lux!!,
                proximity = proximity!!
            )
        }

        if (hasLightReading) {
            return when {
                lux!! < L_OSC ->
                    PhoneContext.COVERED

                lux > L_LUZ ->
                    PhoneContext.UNCOVERED

                else ->
                    PhoneContext.UNKNOWN
            }
        }

        if (hasProximityReading) {
            return when {
                sensorDataSource.isNear(proximity!!) ->
                    PhoneContext.COVERED

                else ->
                    PhoneContext.UNCOVERED
            }
        }

        return PhoneContext.UNKNOWN
    }

    private fun decideWithBoth(
        lux: Float,
        proximity: Float
    ): PhoneContext {
        val near = sensorDataSource.isNear(proximity)

        return when {
            near && lux < L_OSC ->
                PhoneContext.COVERED

            !near || lux > L_LUZ ->
                PhoneContext.UNCOVERED

            else ->
                PhoneContext.UNKNOWN
        }
    }
}