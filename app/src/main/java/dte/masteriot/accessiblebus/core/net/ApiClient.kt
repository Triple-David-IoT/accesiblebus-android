package dte.masteriot.accessiblebus.core.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit routes of K1. Bodies are read by ApiClient so that error bodies can be read too. */
private interface ApiService {
    @GET("sync")
    suspend fun sync(@Query("scope") scope: String, @Query("vehicleId") vehicleId: String?): Response<ResponseBody>

    @GET("requests/{id}")
    suspend fun getRequest(@Path("id") id: String): Response<ResponseBody>

    @GET("trips/{id}/stop-visits")
    suspend fun stopVisits(@Path("id") tripId: String): Response<ResponseBody>

    @POST("requests")
    suspend fun createRequest(@Header(KEY) key: String, @Body body: CreateRequestBody): Response<ResponseBody>

    @POST("requests/{id}/accept")
    suspend fun accept(@Header(KEY) key: String, @Path("id") id: String, @Body body: VersionBody): Response<ResponseBody>

    @POST("requests/{id}/reject")
    suspend fun reject(@Header(KEY) key: String, @Path("id") id: String, @Body body: RejectBody): Response<ResponseBody>

    @POST("requests/{id}/cancel")
    suspend fun cancel(@Header(KEY) key: String, @Path("id") id: String, @Body body: VersionBody): Response<ResponseBody>

    @POST("requests/{id}/complete")
    suspend fun complete(@Header(KEY) key: String, @Path("id") id: String, @Body body: VersionBody): Response<ResponseBody>

    @POST("trips/{id}/advance")
    suspend fun advance(@Header(KEY) key: String, @Path("id") tripId: String, @Body body: AdvanceBody): Response<ResponseBody>

    @POST("trips/{id}/end")
    suspend fun end(@Header(KEY) key: String, @Path("id") tripId: String): Response<ResponseBody>

    @POST("demo/reset")
    suspend fun resetDemo(@Header(KEY) key: String): Response<ResponseBody>

    companion object {
        const val KEY = "Idempotency-Key"
    }
}

/**
 * REST client for the API. [token] is read on every call, so a profile change takes effect
 * without rebuilding the client. Every call returns an [ApiResult] and never throws, except
 * for coroutine cancellation.
 */
class ApiClient(baseUrl: String, private val token: () -> String) : ApiSource {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${token()}").build())
        }
        .build()

    private val service = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(http)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)

    private val requestSerializer = AssistanceRequest.serializer()
    private val tripSerializer = Trip.serializer()

    override suspend fun sync(scope: String, vehicleId: String?): ApiResult<Snapshot> =
        call(Snapshot.serializer()) { service.sync(scope, vehicleId) }

    override suspend fun getRequest(requestId: String): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.getRequest(requestId) }

    override suspend fun stopVisits(tripId: String): ApiResult<List<StopVisit>> =
        call(ListSerializer(StopVisit.serializer())) { service.stopVisits(tripId) }

    override suspend fun createRequest(key: String, stopVisitId: String): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.createRequest(key, CreateRequestBody(stopVisitId, AssistanceType.RAMP_BOARDING)) }

    override suspend fun acceptRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.accept(key, requestId, VersionBody(expectedVersion)) }

    override suspend fun rejectRequest(key: String, requestId: String, expectedVersion: Int, reason: RejectReason): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.reject(key, requestId, RejectBody(expectedVersion, reason)) }

    override suspend fun cancelRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.cancel(key, requestId, VersionBody(expectedVersion)) }

    override suspend fun completeRequest(key: String, requestId: String, expectedVersion: Int): ApiResult<AssistanceRequest> =
        call(requestSerializer) { service.complete(key, requestId, VersionBody(expectedVersion)) }

    override suspend fun advanceTrip(key: String, tripId: String, fromSequence: Int): ApiResult<Trip> =
        call(tripSerializer) { service.advance(key, tripId, AdvanceBody(fromSequence)) }

    override suspend fun endTrip(key: String, tripId: String): ApiResult<Trip> =
        call(tripSerializer) { service.end(key, tripId) }

    override suspend fun resetDemo(key: String): ApiResult<ResetResult> =
        call(ResetResult.serializer()) { service.resetDemo(key) }

    private suspend fun <T> call(serializer: KSerializer<T>, send: suspend () -> Response<ResponseBody>): ApiResult<T> {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // No answer: the command may or may not have run. Retrying with the same key is safe.
            return ApiResult.Failure(e.message ?: "Network error", retryable = true)
        }
        val status = response.code()
        return try {
            if (response.isSuccessful) {
                ApiResult.Success(ApiJson.decodeFromString(serializer, response.body()?.string().orEmpty()))
            } else {
                failure(status, response.errorBody()?.string().orEmpty(), serializer)
            }
        } catch (e: SerializationException) {
            ApiResult.Failure("Unexpected answer: ${e.message}", retryable = false, httpStatus = status)
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure("Unexpected answer: ${e.message}", retryable = false, httpStatus = status)
        } catch (e: IOException) {
            ApiResult.Failure(e.message ?: "Network error", retryable = true, httpStatus = status)
        }
    }

    private fun <T> failure(status: Int, text: String, serializer: KSerializer<T>): ApiResult<T> {
        val error = runCatching { ApiJson.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
        if (status == 409 && error != null) {
            val current = error.currentResource?.let { runCatching { ApiJson.decodeFromJsonElement(serializer, it) }.getOrNull() }
            return ApiResult.Conflict(error.code, error.message, current)
        }
        val retryable = status >= 500 || status == 408 || status == 429
        val message = error?.let { "${it.code}: ${it.message}" } ?: "HTTP $status ${text.take(300)}".trim()
        return ApiResult.Failure(message, retryable, status)
    }
}
