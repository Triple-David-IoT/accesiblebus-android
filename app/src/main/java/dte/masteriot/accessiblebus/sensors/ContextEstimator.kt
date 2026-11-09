package dte.masteriot.accessiblebus.sensors

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

class ContextEstimator(
    private val sensorDataSource: SensorDataSource,
    private val lightDarkThreshold: Float = 5f,
    private val lightBrightThreshold: Float = 20f
) {

    suspend fun estimate(): PhoneContext {

        var latestReading = SensorReading()

        return try {

            // Esperar hasta 1,5 segundos.
            // Si llegan ambas lecturas, podemos decidir antes.
            withTimeoutOrNull(1500L) {

                sensorDataSource.observeReadings()
                    .onEach { reading ->
                        latestReading = reading
                    }
                    .first { reading ->
                        reading.lightLux != null &&
                                reading.proximity != null
                    }
            }

            decideContext(latestReading)

        } catch (e: Exception) {

            // Si ocurre un error inesperado,
            // no debemos bloquear el flujo de avisos.
            PhoneContext.UNKNOWN
        }
    }

    private fun decideContext(
        reading: SensorReading
    ): PhoneContext {

        val light = reading.lightLux
        val proximity = reading.proximity

        // Caso 1: ambos sensores disponibles y con lecturas.
        if (light != null && proximity != null) {

            val isNear = sensorDataSource.isNear(proximity)

            val isDark = light < lightDarkThreshold
            val isBright = light > lightBrightThreshold

            return when {

                isNear && isDark ->
                    PhoneContext.COVERED

                !isNear || isBright ->
                    PhoneContext.UNCOVERED

                else ->
                    PhoneContext.UNKNOWN
            }
        }

        // Caso 2: solamente proximidad.
        if (proximity != null) {

            return if (sensorDataSource.isNear(proximity)) {
                PhoneContext.COVERED
            } else {
                PhoneContext.UNCOVERED
            }
        }

        // Caso 3: solamente luz.
        if (light != null) {

            return when {

                light < lightDarkThreshold ->
                    PhoneContext.COVERED

                light > lightBrightThreshold ->
                    PhoneContext.UNCOVERED

                else ->
                    PhoneContext.UNKNOWN
            }
        }

        // Caso 4: no tenemos lecturas.
        return PhoneContext.UNKNOWN
    }
}