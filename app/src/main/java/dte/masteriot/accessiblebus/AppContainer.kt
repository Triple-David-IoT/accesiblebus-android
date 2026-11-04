package dte.masteriot.accessiblebus

import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.SampleApi

/**
 * Default implementation of AppContainer.
 */
interface AppContainer {

    val apiSource: ApiSource
}

class DefaultAppContainer : AppContainer {

    private val useSampleApi = true

    override val apiSource: ApiSource by lazy {
        if (useSampleApi) {
            SampleApi()
        } else {
            // TODO: Replace with the real API implementation.
            SampleApi()
        }
    }
}