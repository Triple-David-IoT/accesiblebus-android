package dte.masteriot.accessiblebus.sensors

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds
import android.util.Log

class ContextEstimator(
    private val sensorDataSource: SensorDataSource
) {


    companion object {
        private const val TAG = "C08"
        private const val SENSOR_TIMEOUT_MS = 1_500L

        // Umbrales acordados para el proyecto.
        private const val L_OSC = 5f
        private const val L_LUZ = 20f

        // Calibración confirmada: 0 cerca, 5 lejos.
        private const val PROXIMITY_NEAR = 0f
        private const val PROXIMITY_FAR = 5f
    }

    suspend fun estimate(): PhoneContext {
        Log.i(TAG, "========== estimate() INICIO ==========")

        sensorDataSource.logSensorAvailability()

        var latestSnapshot = SensorSnapshot()

        try {
            val result = withTimeoutOrNull(SENSOR_TIMEOUT_MS.milliseconds) {
                sensorDataSource.observeReadings().first { snapshot ->
                    latestSnapshot = snapshot

                    Log.d(
                        TAG,
                        "Estado de lecturas: " +
                                "lux=${snapshot.lightLux}, " +
                                "proximity=${snapshot.proximity}, " +
                                "lightRegistered=${snapshot.lightRegistered}, " +
                                "proximityRegistered=${snapshot.proximityRegistered}"
                    )

                    hasEnoughEvidence(snapshot)
                }
            }

            val snapshot = result ?: latestSnapshot
            val timedOut = result == null

            if (timedOut) {
                Log.w(
                    TAG,
                    "TIMEOUT: no se obtuvo evidencia suficiente " +
                            "en ${SENSOR_TIMEOUT_MS} ms"
                )
            } else {
                Log.i(TAG, "Evidencia suficiente recibida")
            }

// Comprobar si algún sensor registrado no ha proporcionado
// una lectura durante la ventana de adquisición.
            val missingExpectedReading =
                (snapshot.lightRegistered && snapshot.lightLux == null) ||
                        (snapshot.proximityRegistered && snapshot.proximity == null)

            val decision = when {
                timedOut && missingExpectedReading -> {
                    Log.w(
                        TAG,
                        "Evidencia parcial tras timeout. " +
                                "Se devuelve UNKNOWN."
                    )

                    PhoneContext.UNKNOWN
                }

                else -> decide(snapshot)
            }

            Log.i(
                TAG,
                "DECISIÓN: $decision | " +
                        "lux=${snapshot.lightLux} | " +
                        "proximity=${snapshot.proximity} | " +
                        "timedOut=$timedOut"
            )

            return decision
        } finally {
            Log.i(TAG, "estimate() finalizado")

            Log.i(
                TAG,
                "Listeners activos al finalizar: " +
                        sensorDataSource.activeListenersForTest()
            )

            Log.i(TAG, "======================================")
        }
    }

    private fun hasEnoughEvidence(
        snapshot: SensorSnapshot
    ): Boolean {
        val lightExpected = snapshot.lightRegistered
        val proximityExpected = snapshot.proximityRegistered

        // No se pudo registrar ningún sensor.
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

        val hasLightReading = lightAvailable && lux != null
        val hasProximityReading =
            proximityAvailable && proximity != null

        // Ningún sensor registrado o ninguna lectura válida.
        if (!hasLightReading && !hasProximityReading) {
            return PhoneContext.UNKNOWN
        }

        /*
         * Ambos sensores aportan lectura:
         * se aplican las reglas combinadas del proyecto.
         */
        if (hasLightReading && hasProximityReading) {
            return decideWithBoth(
                lux = lux!!,
                proximity = proximity!!
            )
        }

        /*
         * Solo luz: se usa el umbral de oscuridad y el de luz.
         * La zona intermedia no aporta evidencia suficiente.
         */
        if (hasLightReading) {
            return when {
                lux!! < L_OSC -> PhoneContext.COVERED
                lux > L_LUZ -> PhoneContext.UNCOVERED
                else -> PhoneContext.UNKNOWN
            }
        }

        /*
         * Solo proximidad:
         * 0 = cerca, 5 = lejos.
         */
        if (hasProximityReading) {
            return when {
                proximity!! == PROXIMITY_NEAR ->
                    PhoneContext.COVERED

                proximity == PROXIMITY_FAR ->
                    PhoneContext.UNCOVERED

                else -> PhoneContext.UNKNOWN
            }
        }

        return PhoneContext.UNKNOWN
    }

    private fun decideWithBoth(
        lux: Float,
        proximity: Float
    ): PhoneContext {
        return when {
            proximity == PROXIMITY_NEAR && lux < L_OSC ->
                PhoneContext.COVERED

            proximity == PROXIMITY_FAR || lux > L_LUZ ->
                PhoneContext.UNCOVERED

            else ->
                PhoneContext.UNKNOWN
        }
    }
}