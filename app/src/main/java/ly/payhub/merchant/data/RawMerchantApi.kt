package ly.payhub.merchant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Raw authenticated HTTP for the two `/merchant/*` endpoints the 1.1.0 SDK
 * doesn't yet expose:
 *
 *  - `POST   /merchant/devices`                  — register an FCM push token
 *  - `DELETE /merchant/devices` (body `{token}`) — unregister it (token in the
 *    JSON body, not a query string, so it never lands in WAF/access logs)
 *  - `GET    /merchant/dashboard?group_by=sub&window_hours=N` — the per-shop
 *    breakdown (the SDK's `MerchantDashboard` model has no `sub_breakdown` field).
 *
 * TODO(payhub): drop this once SDK 1.2 ships `devices` + sub-breakdown support.
 */
class RawMerchantApi(private val httpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun registerDevice(baseUrl: String, accessToken: String, fcmToken: String): Result<Unit> =
        execUnit(baseUrl, accessToken) { url ->
            Request.Builder()
                .url("${url}/merchant/devices")
                .post(json.encodeToString(DeviceBody(token = fcmToken)).toRequestBody(JSON))
        }

    suspend fun unregisterDevice(baseUrl: String, accessToken: String, fcmToken: String): Result<Unit> =
        execUnit(baseUrl, accessToken) { url ->
            Request.Builder()
                .url("${url}/merchant/devices")
                .delete(json.encodeToString(TokenBody(token = fcmToken)).toRequestBody(JSON))
        }

    suspend fun dashboardBySub(baseUrl: String, accessToken: String, windowHours: Int): Result<SubBreakdownResponse> =
        runCatchingApp {
            withContext(Dispatchers.IO) {
                val u = "${baseUrl.trimEnd('/')}/merchant/dashboard".toHttpUrl().newBuilder()
                    .addQueryParameter("group_by", "sub")
                    .addQueryParameter("window_hours", windowHours.toString())
                    .build()
                val req = Request.Builder().url(u)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val resp = httpClient.newCall(req).await()
                resp.use {
                    val text = it.body?.string().orEmpty()
                    if (it.code !in 200..299) throw httpError(it.code)
                    json.decodeFromString<SubBreakdownResponse>(text)
                }
            }
        }

    private suspend fun execUnit(
        baseUrl: String,
        accessToken: String,
        build: (String) -> Request.Builder,
    ): Result<Unit> = runCatchingApp {
        withContext(Dispatchers.IO) {
            val req = build(baseUrl.trimEnd('/'))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val resp = httpClient.newCall(req).await()
            resp.use {
                if (it.code !in 200..299) throw httpError(it.code)
            }
        }
    }

    private fun httpError(status: Int): RuntimeException = when (status) {
        401 -> AppErrorException(AppError.Unauthorized())
        403 -> AppErrorException(AppError.Forbidden())
        404 -> AppErrorException(AppError.NotFound())
        in 500..599 -> AppErrorException(AppError.Unexpected("PayHub is having trouble right now."))
        else -> AppErrorException(AppError.Unexpected("Request failed (HTTP $status)."))
    }

    @Serializable
    private data class DeviceBody(val platform: String = "android", val token: String)

    @Serializable
    private data class TokenBody(val token: String)

    @Serializable
    data class SubBreakdownResponse(
        @SerialName("window_hours") val windowHours: Int = 24,
        @SerialName("sub_breakdown") val subBreakdown: List<SubBreakdownRow> = emptyList(),
    )

    @Serializable
    data class SubBreakdownRow(
        @SerialName("sub_merchant_id") val subMerchantId: String? = null,
        val code: String? = null,
        val name: String? = null,
        @SerialName("paid_count") val paidCount: Int = 0,
        @SerialName("paid_volume_minor") val paidVolumeMinor: Long = 0,
        val inflight: Int = 0,
        @SerialName("active_pay_links") val activePayLinks: Int = 0,
        @SerialName("needs_followup") val needsFollowup: Int = 0,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(AppErrorException(AppError.Network(cause = e)))
        }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
