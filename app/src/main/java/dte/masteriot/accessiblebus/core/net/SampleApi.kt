package dte.masteriot.accessiblebus.core.net

import dte.masteriot.accessiblebus.core.net.models.StopDto

/**
 * Sample implementation used during development.
 */
class SampleApi : ApiSource {

    override suspend fun getStops(): List<StopDto> {
        return listOf(
            StopDto(
                id = "stop_1",
                name = "Parada 1",
                latitude = 28.1234,
                longitude = -15.4321
            ),
            StopDto(
                id = "stop_2",
                name = "Parada 2",
                latitude = 28.1250,
                longitude = -15.4300
            ),
            StopDto(
                id = "stop_3",
                name = "Parada 3",
                latitude = 28.1270,
                longitude = -15.4280
            ),
            StopDto(
                id = "stop_4",
                name = "Parada 4",
                latitude = 28.1290,
                longitude = -15.4260
            )
        )
    }
}