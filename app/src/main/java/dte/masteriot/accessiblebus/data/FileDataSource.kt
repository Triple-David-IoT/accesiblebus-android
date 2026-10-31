package dte.masteriot.accessiblebus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class FileDataSource(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStops(): StopList = withContext(Dispatchers.IO) {
        val text = context.assets.open("stops.json")
            .bufferedReader().use { it.readText() }
        json.decodeFromString<StopList>(text)
    }
}