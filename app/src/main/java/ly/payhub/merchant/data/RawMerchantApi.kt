package ly.payhub.merchant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * Raw authenticated HTTP for `/merchant/*` endpoints the 1.1.0 SDK doesn't yet
 * expose. The SDK covers `auth` / `payLinks` / `reports.dashboard`; everything
 * else the app needs is wired here until SDK 1.2 lands.
 *
 *  - `POST   /merchant/devices`                  — register an FCM push token
 *  - `DELETE /merchant/devices` (body `{token}`) — unregister it (token in the
 *    JSON body, not a query string, so it never lands in WAF/access logs)
 *  - `GET    /merchant/dashboard?group_by=sub&window_hours=N` — the per-shop
 *    breakdown (the SDK's `MerchantDashboard` model has no `sub_breakdown` field).
 *  - `GET    /merchant/payments`                 — payments list
 *  - `GET    /merchant/payments/{id}`            — payment detail + events + metadata
 *  - `GET    /merchant/settlements`              — settlement files list
 *  - `GET    /merchant/settlements/{id}/rows`    — per-file reconciliation rows
 *
 * The Pydantic response models are mirrored 1:1 below — field names match the
 * server JSON via `@SerialName`. Extra server fields are tolerated
 * (`ignoreUnknownKeys = true`) so a server-side superset doesn't break a stale
 * client.
 *
 * TODO(payhub): drop these once SDK 1.2 ships `payments`, `settlements`,
 * `devices`, and sub-breakdown support.
 */
class RawMerchantApi(private val httpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ---------------------------------------------------------------- devices

    suspend fun registerDevice(baseUrl: String, accessToken: String, fcmToken: String): Result<Unit> =
        execUnit(baseUrl, accessToken) { url ->
            Request.Builder()
                .url("$url/merchant/devices")
                .post(json.encodeToString(DeviceBody(token = fcmToken)).toRequestBody(JSON))
        }

    suspend fun unregisterDevice(baseUrl: String, accessToken: String, fcmToken: String): Result<Unit> =
        execUnit(baseUrl, accessToken) { url ->
            Request.Builder()
                .url("$url/merchant/devices")
                .delete(json.encodeToString(TokenBody(token = fcmToken)).toRequestBody(JSON))
        }

    // ---------------------------------------------------------------- dashboard (parent breakdown)

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

    // ---------------------------------------------------------------- payments

    suspend fun listPayments(
        baseUrl: String,
        accessToken: String,
        psp: String? = null,
        status: String? = null,
        limit: Int = DEFAULT_PAGE,
        offset: Int = 0,
    ): Result<List<PaymentRow>> = getJson(baseUrl, accessToken, "/merchant/payments") { b ->
        if (!psp.isNullOrBlank()) b.addQueryParameter("psp", psp)
        if (!status.isNullOrBlank()) b.addQueryParameter("status", status)
        b.addQueryParameter("limit", clamp(limit, 1, MAX_PAYMENTS_PAGE).toString())
        b.addQueryParameter("offset", clamp(offset, 0, Int.MAX_VALUE).toString())
    }

    suspend fun getPayment(baseUrl: String, accessToken: String, id: String): Result<PaymentDetail> =
        getJson(baseUrl, accessToken, "/merchant/payments/$id") { /* no query */ }

    // ---------------------------------------------------------------- settlements

    suspend fun listSettlements(
        baseUrl: String,
        accessToken: String,
        psp: String? = null,
        limit: Int = DEFAULT_PAGE,
        offset: Int = 0,
    ): Result<List<SettlementFile>> = getJson(baseUrl, accessToken, "/merchant/settlements") { b ->
        if (!psp.isNullOrBlank()) b.addQueryParameter("psp", psp)
        b.addQueryParameter("limit", clamp(limit, 1, MAX_SETTLEMENTS_PAGE).toString())
        b.addQueryParameter("offset", clamp(offset, 0, Int.MAX_VALUE).toString())
    }

    suspend fun getSettlement(baseUrl: String, accessToken: String, fileId: String): Result<SettlementFile> =
        getJson(baseUrl, accessToken, "/merchant/settlements/$fileId") { /* no query */ }

    suspend fun listSettlementRows(
        baseUrl: String,
        accessToken: String,
        fileId: String,
        statusFilter: String? = null,
        limit: Int = DEFAULT_ROWS_PAGE,
        offset: Int = 0,
    ): Result<List<SettlementRow>> = getJson(baseUrl, accessToken, "/merchant/settlements/$fileId/rows") { b ->
        if (!statusFilter.isNullOrBlank()) b.addQueryParameter("status_filter", statusFilter)
        b.addQueryParameter("limit", clamp(limit, 1, MAX_ROWS_PAGE).toString())
        b.addQueryParameter("offset", clamp(offset, 0, Int.MAX_VALUE).toString())
    }

    // ---------------------------------------------------------------- internals

    private suspend inline fun <reified T> getJson(
        baseUrl: String,
        accessToken: String,
        path: String,
        crossinline build: (okhttp3.HttpUrl.Builder) -> Unit,
    ): Result<T> = runCatchingApp {
        withContext(Dispatchers.IO) {
            val builder = "${baseUrl.trimEnd('/')}$path".toHttpUrl().newBuilder()
            build(builder)
            val req = Request.Builder().url(builder.build())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val resp = httpClient.newCall(req).await()
            resp.use {
                val text = it.body?.string().orEmpty()
                if (it.code !in 200..299) throw httpError(it.code)
                json.decodeFromString<T>(text)
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

    private fun clamp(v: Int, lo: Int, hi: Int): Int = v.coerceIn(lo, hi)

    // ---------------------------------------------------------------- request bodies

    @Serializable
    private data class DeviceBody(val platform: String = "android", val token: String)

    @Serializable
    private data class TokenBody(val token: String)

    // ---------------------------------------------------------------- response models

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

    /** Mirrors `app/api/merchant/payments.py::PaymentRowOut`. */
    @Serializable
    data class PaymentRow(
        val id: String,
        @SerialName("psp_code") val pspCode: String,
        @SerialName("psp_ref") val pspRef: String? = null,
        @SerialName("merchant_order_ref") val merchantOrderRef: String,
        @SerialName("amount_minor") val amountMinor: Long,
        val currency: String,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    /** Mirrors `app/api/merchant/payments.py::PaymentEventOut`. */
    @Serializable
    data class PaymentEvent(
        val id: String,
        @SerialName("event_type") val eventType: String,
        @SerialName("prev_status") val prevStatus: String? = null,
        @SerialName("new_status") val newStatus: String? = null,
        val source: String,
        @SerialName("created_at") val createdAt: String,
    )

    /**
     * Mirrors `app/api/merchant/payments.py::PaymentDetailOut`. Kept flat
     * (not extending [PaymentRow]) because kotlinx-serialization handles flat
     * data classes more cleanly across `@SerialName`-renamed fields.
     */
    @Serializable
    data class PaymentDetail(
        val id: String,
        @SerialName("psp_code") val pspCode: String,
        @SerialName("psp_ref") val pspRef: String? = null,
        @SerialName("merchant_order_ref") val merchantOrderRef: String,
        @SerialName("amount_minor") val amountMinor: Long,
        val currency: String,
        val status: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        val events: List<PaymentEvent> = emptyList(),
        val metadata: JsonObject = JsonObject(emptyMap()),
    ) {
        /** Convenience: project to a list-row shape so list/detail can share UI bits. */
        fun toRow(): PaymentRow = PaymentRow(
            id = id, pspCode = pspCode, pspRef = pspRef, merchantOrderRef = merchantOrderRef,
            amountMinor = amountMinor, currency = currency, status = status,
            createdAt = createdAt, updatedAt = updatedAt,
        )
    }

    /** Mirrors `app/api/merchant/settlements.py::SettlementFileOut`. */
    @Serializable
    data class SettlementFile(
        val id: String,
        @SerialName("psp_code") val pspCode: String,
        val filename: String,
        @SerialName("file_sha256") val fileSha256: String,
        @SerialName("period_from") val periodFrom: String? = null,
        @SerialName("period_to") val periodTo: String? = null,
        @SerialName("row_count") val rowCount: Int = 0,
        @SerialName("matched_count") val matchedCount: Int = 0,
        @SerialName("mismatch_count") val mismatchCount: Int = 0,
        @SerialName("missing_in_hub_count") val missingInHubCount: Int = 0,
        @SerialName("missing_in_psp_count") val missingInPspCount: Int = 0,
        @SerialName("created_at") val createdAt: String,
    )

    /** Mirrors `app/api/merchant/settlements.py::SettlementRowOut`. */
    @Serializable
    data class SettlementRow(
        val id: String,
        @SerialName("merchant_order_ref") val merchantOrderRef: String? = null,
        @SerialName("psp_ref") val pspRef: String? = null,
        @SerialName("psp_status") val pspStatus: String? = null,
        @SerialName("amount_minor") val amountMinor: Long? = null,
        val currency: String? = null,
        @SerialName("payment_id") val paymentId: String? = null,
        val status: String,
        val diff: JsonObject = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_PAGE = 50
        const val MAX_PAYMENTS_PAGE = 200
        const val MAX_SETTLEMENTS_PAGE = 200
        const val DEFAULT_ROWS_PAGE = 100
        const val MAX_ROWS_PAGE = 1000
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
