package dte.masteriot.accessiblebus

import android.content.Context
import android.util.Log
import dte.masteriot.accessiblebus.core.data.DecisionAnnouncer
import dte.masteriot.accessiblebus.core.data.MqttEventFeed
import dte.masteriot.accessiblebus.core.data.PreferencesAnnouncedDecisions
import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // Lives as long as the app: events and the /sync after a reconnection run here.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Passenger identity (K7) until the profile (C07) says whether this phone is a passenger or a bus.
    override val assistanceRepository: AssistanceRepository by lazy {
        val live = !BuildConfig.USE_SAMPLE_API
        AssistanceRepository(
            api = apiSource,
            keys = PreferencesCommandKeys(context),
            events = if (live) MqttEventFeed(BusMqttClient(BuildConfig.MQTT_BROKER, "app-passenger-1", appScope), appScope) else null,
            eventsTopic = "users/passenger-1/events",
            // Until the AnnouncementCoordinator (C) exists, decisions only go to the log.
            announcer = DecisionAnnouncer { Log.i("Announcement", "announce $it") },
            announced = PreferencesAnnouncedDecisions(context),
            scope = appScope,
        )
    }
}
