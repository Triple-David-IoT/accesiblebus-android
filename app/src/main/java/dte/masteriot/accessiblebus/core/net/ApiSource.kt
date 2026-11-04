package dte.masteriot.accessiblebus.core.net

import dte.masteriot.accessiblebus.core.net.models.StopDto

/**
 * Abstraction for the remote data source.
 *
 * Implementations can retrieve data from a real API
 * or provide sample data for development.
 */
interface ApiSource {

    /**
     * Retrieves the available bus stops.
     */
    suspend fun getStops(): List<StopDto>
}