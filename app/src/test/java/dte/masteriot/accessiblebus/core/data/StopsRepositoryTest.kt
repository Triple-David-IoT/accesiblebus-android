package dte.masteriot.accessiblebus.core.data

import dte.masteriot.accessiblebus.core.net.ApiJson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StopsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val manifestText = File("../contracts/manifest.json").readText()
    private val manifest = ApiJson.decodeFromString(StopsManifest.serializer(), manifestText)
    private val geojson = File("../contracts/samples/stops.geojson").readText()

    private fun repository(fetch: suspend (String) -> String) =
        StopsRepository(manifest, File(folder.root, "stops.json"), now = { "2026-11-08T12:00:00Z" }, fetch = fetch)

    @Test
    fun appManifestIsTheContractOne() {
        val asset = File("src/main/assets/manifest.json").readText()
        assertEquals(manifestText.replace("\r\n", "\n"), asset.replace("\r\n", "\n"))
    }

    @Test
    fun queryIsTheOneCheckedInA01() {
        val url = repository { "" }.sourceUrl.toHttpUrl()
        assertEquals("/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer/0/query", url.encodedPath)
        assertEquals("CODIGOESTACION IN ('456','459','460','461')", url.queryParameter("where"))
        assertEquals("CODIGOESTACION,DENOMINACION", url.queryParameter("outFields"))
        assertEquals("4326", url.queryParameter("outSR"))
        assertEquals("geojson", url.queryParameter("f"))
    }

    @Test
    fun realAnswerIsSortedByTheManifest() {
        // The service returns 459, 460, 461, 456.
        val stops = repository { "" }.parse(geojson)

        assertEquals(listOf("456", "459", "460", "461"), stops.map { it.code })
        assertEquals(listOf(1, 2, 3, 4), stops.map { it.sequence })
        assertEquals(listOf("76", "77", "79", "81"), stops.map { it.emtStopCode })
        assertEquals("Pº PRADO-BANCO DE ESPAÑA", stops.first().name)
        // GeoJSON is [longitude, latitude]: Madrid is near 40.4 N, 3.7 W.
        assertTrue(stops.all { it.latitude in 40.40..40.43 && it.longitude in -3.70..-3.68 })
    }

    @Test
    fun downloadIsSavedAndUsedWithoutNetwork() = runTest {
        val fresh = repository { geojson }.load() as StopsState.Ready
        assertFalse(fresh.fromCache)

        val offline = repository { throw IOException("Unable to resolve host") }.load() as StopsState.Ready
        assertTrue(offline.fromCache)
        assertEquals("2026-11-08T12:00:00Z", offline.copy.fetchedAt)
        assertEquals(fresh.copy.stops, offline.copy.stops)
        assertEquals("Unable to resolve host", offline.problem)
    }

    @Test
    fun incompleteAnswerIsNeverShownAsComplete() = runTest {
        val threeStops = geojson.replace("\"CODIGOESTACION\":\"461\"", "\"CODIGOESTACION\":\"999\"")

        val failed = repository { threeStops }.load() as StopsState.Failed
        assertTrue(failed.problem, failed.problem.contains("461"))

        repository { geojson }.load()
        val fallback = repository { threeStops }.load() as StopsState.Ready
        assertTrue(fallback.fromCache)
        assertEquals(4, fallback.copy.stops.size)
    }

    @Test
    fun brokenCacheIsIgnored() = runTest {
        File(folder.root, "stops.json").writeText("{not json")
        assertTrue(repository { throw IOException("offline") }.load() is StopsState.Failed)
    }
}
