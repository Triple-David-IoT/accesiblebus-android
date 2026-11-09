package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiJson
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

// ---- Manifest (contracts/manifest.json, shared with the API)

@Serializable
data class ManifestStop(val sequence: Int, val physicalStopCode: String, val emtStopCode: String)

@Serializable
data class StopsManifest(
    val line: String,
    val direction: String,
    val directionLabel: String,
    val itineraryCode: Int,
    val sourceDataDate: String,
    val stops: List<ManifestStop>,
)

// ---- CRTM GeoJSON: only the fields the app uses (docs/datos.md)

@Serializable
private data class FeatureCollection(val features: List<Feature> = emptyList())

@Serializable
private data class Feature(val geometry: Geometry, val properties: Properties)

@Serializable
private data class Geometry(val coordinates: List<Double>)

@Serializable
private data class Properties(
    @SerialName("CODIGOESTACION") val code: String,
    @SerialName("DENOMINACION") val name: String,
)

// ---- What the screens get

@Serializable
data class Stop(
    val sequence: Int,
    /** CRTM CODIGOESTACION, the physicalStopCode of the API. */
    val code: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Public EMT number. Never send it to the API, nor the CRTM code to EMT. */
    val emtStopCode: String,
)

/** Cached copy, with when and where it came from. */
@Serializable
data class StopsCopy(val fetchedAt: String, val source: String, val stops: List<Stop>)

sealed interface StopsState {
    data object Loading : StopsState

    /** [fromCache] is true when the download failed and this is the copy saved earlier. */
    data class Ready(val copy: StopsCopy, val fromCache: Boolean, val problem: String? = null) : StopsState

    data class Failed(val problem: String) : StopsState
}

private interface CrtmService {
    @GET
    suspend fun get(@Url url: String): ResponseBody
}

/**
 * Stops of the demo line (A10). Downloads them from the CRTM service with the A01 query, keeps
 * only code, name and coordinates, sorts them by the manifest and saves a copy in [cacheFile].
 * Without network, or with an incomplete answer, it shows that copy labelled as local; it never
 * makes up a stop that the source did not return.
 *
 * [fetch] returns the body of a GET; the default uses Retrofit with the full URL.
 */
class StopsRepository(
    private val manifest: StopsManifest,
    private val cacheFile: File,
    private val now: () -> String,
    private val fetch: suspend (String) -> String = retrofitFetch(),
) {
    private val _state = MutableStateFlow<StopsState>(StopsState.Loading)
    val state: StateFlow<StopsState> = _state.asStateFlow()

    /** The A01 query: the manifest stops of layer 0, as GeoJSON in WGS84. */
    val sourceUrl: String = SERVICE.toHttpUrl().newBuilder()
        .addQueryParameter("where", "CODIGOESTACION IN (${manifest.stops.joinToString(",") { "'${it.physicalStopCode}'" }})")
        .addQueryParameter("outFields", "CODIGOESTACION,DENOMINACION")
        .addQueryParameter("outSR", "4326")
        .addQueryParameter("f", "geojson")
        .build()
        .toString()

    suspend fun load(): StopsState {
        val result = try {
            val copy = StopsCopy(now(), sourceUrl, parse(fetch(sourceUrl)))
            withContext(Dispatchers.IO) { save(copy) }
            StopsState.Ready(copy, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = e.message ?: e.javaClass.simpleName
            withContext(Dispatchers.IO) { cached() }?.let { StopsState.Ready(it, fromCache = true, problem = problem) }
                ?: StopsState.Failed(problem)
        }
        _state.value = result
        return result
    }

    /** Keeps the manifest stops in manifest order. Fails if any is missing. */
    fun parse(text: String): List<Stop> {
        val byCode = ApiJson.decodeFromString(FeatureCollection.serializer(), text).features.associateBy { it.properties.code }
        val missing = manifest.stops.map { it.physicalStopCode }.filter { it !in byCode }
        if (missing.isNotEmpty()) throw IllegalStateException("Faltan paradas en la respuesta: ${missing.joinToString()}")
        return manifest.stops.sortedBy { it.sequence }.map { m ->
            val feature = byCode.getValue(m.physicalStopCode)
            val (longitude, latitude) = feature.geometry.coordinates
            Stop(m.sequence, m.physicalStopCode, feature.properties.name.trim(), latitude, longitude, m.emtStopCode)
        }
    }

    private fun save(copy: StopsCopy) {
        val temporary = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        temporary.writeText(ApiJson.encodeToString(StopsCopy.serializer(), copy))
        if (!temporary.renameTo(cacheFile)) {
            cacheFile.delete()
            temporary.renameTo(cacheFile)
        }
    }

    /** The saved copy, only if it still has every manifest stop. */
    private fun cached(): StopsCopy? = runCatching {
        ApiJson.decodeFromString(StopsCopy.serializer(), cacheFile.readText())
    }.getOrNull()?.takeIf { copy -> copy.stops.map { it.code }.toSet() == manifest.stops.map { it.physicalStopCode }.toSet() }

    companion object {
        const val SERVICE = "https://services5.arcgis.com/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer/0/query"

        fun retrofitFetch(): suspend (String) -> String {
            val service = Retrofit.Builder()
                .baseUrl("https://services5.arcgis.com/")
                .client(
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .build(),
                )
                .build()
                .create(CrtmService::class.java)
            return { url -> service.get(url).use { it.string() } }
        }
    }
}
