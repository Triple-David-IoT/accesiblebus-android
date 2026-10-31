package dte.masteriot.accessiblebus.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

interface StopsApiService {
    @GET("api/stops")
    suspend fun getStops(): StopList
}

object NetworkDataSource {
    private const val BASE_URL = "http://10.0.2.2:1880/"
    private val json = Json { ignoreUnknownKeys = true }

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val service = retrofit.create(StopsApiService::class.java)

    suspend fun getStops(): StopList = service.getStops()
}