package dte.masteriot.accessiblebus

import android.content.Context
import dte.masteriot.accessiblebus.core.data.AssistanceRepository
import dte.masteriot.accessiblebus.core.data.PreferencesCommandKeys
import dte.masteriot.accessiblebus.core.net.ApiClient
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.SampleApi
import dte.masteriot.accessiblebus.data.FileDataSource
import dte.masteriot.accessiblebus.data.NetworkDataSource
import dte.masteriot.accessiblebus.data.Repository

/**
 * Container for Dependency Injection holding app-wide data sources and repositories.
 */
interface AppContainer {
    val repository: Repository
    val networkDataSource: NetworkDataSource
    val fileDataSource: FileDataSource
    val apiSource: ApiSource
    val assistanceRepository: AssistanceRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val repository: Repository by lazy {
        Repository(context)
    }

    override val networkDataSource: NetworkDataSource by lazy {
        NetworkDataSource
    }

    override val fileDataSource: FileDataSource by lazy {
        FileDataSource(context)
    }

    override val apiSource: ApiSource by lazy {
        if (BuildConfig.USE_SAMPLE_API) {
            SampleApi { name -> context.assets.open("samples/$name").bufferedReader().use { it.readText() } }
        } else {
            ApiClient(BuildConfig.API_BASE_URL) { BuildConfig.API_TOKEN }
        }
    }

    // Passenger scope until the profile (C07) says whether this phone is a passenger or a bus.
    override val assistanceRepository: AssistanceRepository by lazy {
        AssistanceRepository(apiSource, PreferencesCommandKeys(context))
    }
}
