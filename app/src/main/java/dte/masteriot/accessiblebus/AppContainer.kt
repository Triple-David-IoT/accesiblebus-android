package dte.masteriot.accessiblebus

import android.content.Context
import dte.masteriot.accessiblebus.announcements.AnnouncementCoordinator
import dte.masteriot.accessiblebus.core.data.MqttEventFeed
import dte.masteriot.accessiblebus.core.data.PreferencesAnnouncedDecisions
import dte.masteriot.accessiblebus.core.data.StopsManifest
import dte.masteriot.accessiblebus.core.data.StopsRepository
import dte.masteriot.accessiblebus.core.net.ApiJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import dte.masteriot.accessiblebus.core.mqtt.BusMqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dte.masteriot.accessiblebus.core.data.AssistanceRepository
import dte.masteriot.accessiblebus.core.data.PreferencesCommandKeys
import dte.masteriot.accessiblebus.core.net.ApiClient
import dte.masteriot.accessiblebus.core.net.ApiSource
import dte.masteriot.accessiblebus.core.net.SampleApi
import dte.masteriot.accessiblebus.core.config.AppConfigRepository
import dte.masteriot.accessiblebus.hmi.HmiLink
import dte.masteriot.accessiblebus.hmi.ReceiptSender
import dte.masteriot.accessiblebus.sensors.ContextEstimator
import dte.masteriot.accessiblebus.sensors.SensorDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Container for Dependency Injection holding app-wide data sources and repositories.
 */
interface AppContainer {
    val sensorDataSource: SensorDataSource
    val contextEstimator: ContextEstimator
    val appConfigRepository: AppConfigRepository
    val apiSource: ApiSource
    val assistanceRepository: AssistanceRepository
    val driverRepository: AssistanceRepository
    val receiptSender: ReceiptSender
    val stopsRepository: StopsRepository
    val hmiLink: HmiLink

    val announcementAvailability: StateFlow<Boolean>
    val announcementCoordinator: AnnouncementCoordinator


    fun setAnnouncementAvailability(isAvailable: Boolean)

    /** The app is visible again: /sync the repositories in use, since events may have been missed. */
    fun refreshOnReturn()
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val appConfigRepository: AppConfigRepository by lazy {
        AppConfigRepository(context)
    }

    // The laboratory saved in Preferences, read once: a change there applies when the app restarts.
    // An empty field falls back to the build default (the laptop hotspot, 192.168.137.1).
    private val connection by lazy {
        runBlocking { appConfigRepository.getConfig(appConfigRepository.activeProfile().first()) }
    }
    private val apiUrl by lazy { connection.apiUrl.trim().ifBlank { BuildConfig.API_BASE_URL } }
    // The broker field takes "host" or "host:port", with or without "tcp://".
    private val mqttBroker by lazy {
        val host = connection.mqttHost.trim().substringAfter("://")
        when {
            host.isBlank() -> BuildConfig.MQTT_BROKER
            ':' in host -> "tcp://$host"
            else -> "tcp://$host:${connection.mqttPort}"
        }
    }

    override val apiSource: ApiSource by lazy {
        if (BuildConfig.USE_SAMPLE_API) {
            SampleApi { name -> context.assets.open("samples/$name").bufferedReader().use { it.readText() } }
        } else {
            ApiClient(apiUrl) { BuildConfig.API_TOKEN }
        }
    }

    override val stopsRepository: StopsRepository by lazy {
        val manifest = context.assets.open("manifest.json").bufferedReader().use { it.readText() }
        StopsRepository(
            manifest = ApiJson.decodeFromString(StopsManifest.serializer(), manifest),
            cacheFile = File(context.filesDir, "stops_cache.json"),
            now = {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
            },
        )
    }

    // Lives as long as the app: events and the /sync after a reconnection run here.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The demo identities are fixed (K7): passenger-1 here, driver-1 and hmi-demo-01 for the bus.
    private val passenger = lazy {
        val live = !BuildConfig.USE_SAMPLE_API
        AssistanceRepository(
            api = apiSource,
            keys = PreferencesCommandKeys(context),
            events = if (live) MqttEventFeed(BusMqttClient(mqttBroker, "app-passenger-1", appScope), appScope) else null,
            eventsTopic = "users/passenger-1/events",
            announcer = announcementCoordinator,
            scope = appScope,
        )
    }
    override val assistanceRepository: AssistanceRepository by passenger

    override val hmiLink by lazy { HmiLink(mqttBroker, "hmi-demo-01", "BUS-DEMO-01", appScope) }

    // Sample mode shares the passenger's SampleApi.
    private val driverApi: ApiSource by lazy {
        if (BuildConfig.USE_SAMPLE_API) apiSource else ApiClient(apiUrl) { "demo-conductor-1" }
    }

    private val driver = lazy {
        AssistanceRepository(
            api = driverApi,
            keys = PreferencesCommandKeys(context, name = "driver_command_keys"),
            syncScope = "vehicle",
            vehicleId = "BUS-DEMO-01",
            events = if (!BuildConfig.USE_SAMPLE_API) hmiLink.feed else null,
            eventsTopic = "vehicles/BUS-DEMO-01/events",
            scope = appScope,
        ).also { repository ->
            hmiLink.afterReconnect = {
                repository.refresh()
                receiptSender.check(forgetSent = true)
            }
        }
    }
    override val driverRepository: AssistanceRepository by driver

    override val receiptSender: ReceiptSender by lazy {
        ReceiptSender(driverRepository.snapshot, hmiLink, appScope, enabled = !BuildConfig.USE_SAMPLE_API)
    }

    private val _announcementAvailability = MutableStateFlow(false)

    override val announcementAvailability: StateFlow<Boolean> =
        _announcementAvailability.asStateFlow()

    override fun setAnnouncementAvailability(isAvailable: Boolean) {
        _announcementAvailability.value = isAvailable
    }

    // Only the repositories this phone already uses: touching the driver's would connect it as the HMI.
    override fun refreshOnReturn() {
        appScope.launch {
            if (passenger.isInitialized()) passenger.value.refresh()
            if (driver.isInitialized()) {
                driver.value.refresh()
                receiptSender.check(forgetSent = true)
            }
        }
    }

    override val sensorDataSource by lazy {
        SensorDataSource(context.applicationContext)
    }

    override val contextEstimator by lazy {
        ContextEstimator(sensorDataSource)
    }

    override val announcementCoordinator by lazy {
        AnnouncementCoordinator(
            configRepository = appConfigRepository,
            contextEstimator = contextEstimator,
            availability = announcementAvailability,
            scope = appScope,
            context = context.applicationContext,
            announced = PreferencesAnnouncedDecisions(context),
        )
    }
}
