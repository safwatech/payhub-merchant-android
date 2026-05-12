package ly.payhub.merchant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
 *  - `POST   /merchant/auth/change-password`     — change the account password
 *  - `POST   /merchant/auth/mfa/{enrol,confirm,disable}` — two-factor management
 *  - `GET/PATCH /merchant/org`                   — organisation-profile view/edit
 *  - `GET/POST /merchant/sub-merchants`, `GET/PATCH/DELETE /merchant/sub-merchants/{id}`
 *  - `GET/POST /merchant/sub-merchants/{sid}/users`, `PATCH/DELETE .../users/{uid}`,
 *    `POST .../users/{uid}/{reissue-invite,clear-mfa}` — sub-user management
 *
 * The Pydantic response models are mirrored 1:1 below — field names match the
 * server JSON via `@SerialName`. Extra server fields are tolerated
 * (`ignoreUnknownKeys = true`) so a server-side superset doesn't break a stale
 * client.
 *
 * TODO(payhub): drop these once SDK 1.2 ships `payments`, `settlements`,
 * `devices`, sub-breakdown, account/MFA, org and sub-merchant management.
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
                    if (it.code !in 200..299) throw httpError(it.code, text)
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

    // ---------------------------------------------------------------- account / MFA (raw — SDK 1.2)

    // TODO(payhub): fold into SDK 1.2
    suspend fun changePassword(
        baseUrl: String,
        accessToken: String,
        oldPassword: String,
        newPassword: String,
        code: String?,
    ): Result<Unit> = postUnit(
        baseUrl, accessToken, "/merchant/auth/change-password",
        json.encodeToString(ChangePasswordBody(oldPassword = oldPassword, newPassword = newPassword, code = code)),
    )

    // TODO(payhub): fold into SDK 1.2
    suspend fun mfaEnrol(baseUrl: String, accessToken: String): Result<MfaEnrol> =
        postJson(baseUrl, accessToken, "/merchant/auth/mfa/enrol")

    // TODO(payhub): fold into SDK 1.2
    suspend fun mfaConfirm(baseUrl: String, accessToken: String, code: String): Result<Unit> =
        postUnit(baseUrl, accessToken, "/merchant/auth/mfa/confirm", json.encodeToString(CodeBody(code)))

    // TODO(payhub): fold into SDK 1.2
    suspend fun mfaDisable(baseUrl: String, accessToken: String, password: String): Result<Unit> =
        postUnit(baseUrl, accessToken, "/merchant/auth/mfa/disable", json.encodeToString(PasswordBody(password)))

    // ---------------------------------------------------------------- organisation profile (raw — SDK 1.2)

    // TODO(payhub): fold into SDK 1.2
    suspend fun getOrg(baseUrl: String, accessToken: String): Result<OrgInfo> =
        getJson(baseUrl, accessToken, "/merchant/org") { }

    // TODO(payhub): fold into SDK 1.2
    suspend fun updateOrg(baseUrl: String, accessToken: String, patch: OrgPatch): Result<OrgInfo> =
        patchJson(baseUrl, accessToken, "/merchant/org", json.encodeToString(patch))

    // ---------------------------------------------------------------- sub-merchants (raw — SDK 1.2)

    // TODO(payhub): fold into SDK 1.2
    suspend fun listSubMerchants(baseUrl: String, accessToken: String): Result<List<SubMerchant>> =
        getJson(baseUrl, accessToken, "/merchant/sub-merchants") { }

    // TODO(payhub): fold into SDK 1.2
    suspend fun getSubMerchant(baseUrl: String, accessToken: String, id: String): Result<SubMerchant> =
        getJson(baseUrl, accessToken, "/merchant/sub-merchants/$id") { }

    // TODO(payhub): fold into SDK 1.2
    suspend fun createSubMerchant(baseUrl: String, accessToken: String, body: SubMerchantCreate): Result<SubMerchant> =
        postJson(baseUrl, accessToken, "/merchant/sub-merchants", json.encodeToString(body))

    // TODO(payhub): fold into SDK 1.2
    suspend fun updateSubMerchant(
        baseUrl: String,
        accessToken: String,
        id: String,
        body: SubMerchantPatch,
    ): Result<SubMerchant> =
        patchJson(baseUrl, accessToken, "/merchant/sub-merchants/$id", json.encodeToString(body))

    // TODO(payhub): fold into SDK 1.2
    suspend fun deleteSubMerchant(baseUrl: String, accessToken: String, id: String): Result<Unit> =
        deleteUnit(baseUrl, accessToken, "/merchant/sub-merchants/$id")

    // ---------------------------------------------------------------- sub-users (raw — SDK 1.2)

    // TODO(payhub): fold into SDK 1.2
    suspend fun listSubUsers(baseUrl: String, accessToken: String, subId: String): Result<List<SubUser>> =
        getJson(baseUrl, accessToken, "/merchant/sub-merchants/$subId/users") { }

    // TODO(payhub): fold into SDK 1.2
    suspend fun createSubUser(
        baseUrl: String,
        accessToken: String,
        subId: String,
        body: SubUserCreate,
    ): Result<SubUserCreated> =
        postJson(baseUrl, accessToken, "/merchant/sub-merchants/$subId/users", json.encodeToString(body))

    // TODO(payhub): fold into SDK 1.2
    suspend fun updateSubUser(
        baseUrl: String,
        accessToken: String,
        subId: String,
        uid: String,
        body: SubUserPatch,
    ): Result<SubUser> =
        patchJson(baseUrl, accessToken, "/merchant/sub-merchants/$subId/users/$uid", json.encodeToString(body))

    // TODO(payhub): fold into SDK 1.2
    suspend fun disableSubUser(
        baseUrl: String,
        accessToken: String,
        subId: String,
        uid: String,
    ): Result<Unit> = deleteUnit(baseUrl, accessToken, "/merchant/sub-merchants/$subId/users/$uid")

    // TODO(payhub): fold into SDK 1.2
    suspend fun reissueSubUserInvite(
        baseUrl: String,
        accessToken: String,
        subId: String,
        uid: String,
    ): Result<ReissueInvite> =
        postJson(baseUrl, accessToken, "/merchant/sub-merchants/$subId/users/$uid/reissue-invite")

    // TODO(payhub): fold into SDK 1.2
    suspend fun clearSubUserMfa(
        baseUrl: String,
        accessToken: String,
        subId: String,
        uid: String,
        code: String,
    ): Result<Unit> = postUnit(
        baseUrl, accessToken, "/merchant/sub-merchants/$subId/users/$uid/clear-mfa", json.encodeToString(CodeBody(code)),
    )

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
                if (it.code !in 200..299) throw httpError(it.code, text)
                json.decodeFromString<T>(text)
            }
        }
    }

    /** `POST <path>` with a JSON body (null → `{}`), decoding the response into [T]. */
    private suspend inline fun <reified T> postJson(
        baseUrl: String,
        accessToken: String,
        path: String,
        body: String = "{}",
    ): Result<T> = bodyMethod(baseUrl, accessToken, path, body) { req, b -> req.post(b) }
        .mapCatching { json.decodeFromString<T>(it) }

    /** `PATCH <path>` with a JSON body, decoding the response into [T]. */
    private suspend inline fun <reified T> patchJson(
        baseUrl: String,
        accessToken: String,
        path: String,
        body: String,
    ): Result<T> = bodyMethod(baseUrl, accessToken, path, body) { req, b -> req.patch(b) }
        .mapCatching { json.decodeFromString<T>(it) }

    /** `POST <path>` expecting an empty (204) response. */
    private suspend fun postUnit(
        baseUrl: String,
        accessToken: String,
        path: String,
        body: String = "{}",
    ): Result<Unit> = bodyMethod(baseUrl, accessToken, path, body) { req, b -> req.post(b) }.map { }

    /** `DELETE <path>` (optional body) expecting an empty (204) response. */
    private suspend fun deleteUnit(
        baseUrl: String,
        accessToken: String,
        path: String,
        body: String? = null,
    ): Result<Unit> = bodyMethod(baseUrl, accessToken, path, body) { req, b ->
        if (body == null) req.delete() else req.delete(b)
    }.map { }

    /** Fire `method` with `body` (JSON), status-check, and return the response text. */
    private suspend fun bodyMethod(
        baseUrl: String,
        accessToken: String,
        path: String,
        body: String?,
        method: (Request.Builder, okhttp3.RequestBody) -> Request.Builder,
    ): Result<String> = runCatchingApp {
        withContext(Dispatchers.IO) {
            val req = method(
                Request.Builder().url("${baseUrl.trimEnd('/')}$path"),
                (body ?: "").toRequestBody(JSON),
            )
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val resp = httpClient.newCall(req).await()
            resp.use {
                val text = it.body?.string().orEmpty()
                if (it.code !in 200..299) throw httpError(it.code, text)
                text
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
                val text = it.body?.string().orEmpty()
                if (it.code !in 200..299) throw httpError(it.code, text)
            }
        }
    }

    /**
     * Map a non-2xx response to a typed [AppError], parsing the error envelope
     * (`{"error":{"code","message","details"}}`, FastAPI's `{"detail":...}`, or
     * a generic `"HTTP <status>"`). Note the 401 special-cases: a 401 from
     * change-password / mfa-confirm / clear-mfa is **not** a session expiry —
     * `hub.merchant.mfa_required` becomes [AppError.MfaRequired] and
     * `hub.merchant.bad_mfa` / `hub.merchant.bad_credentials` become
     * [AppError.Invalid], so the caller re-prompts rather than logging out.
     */
    private fun httpError(status: Int, body: String): RuntimeException {
        val (code, message) = parseServerMessage(body, status)
        return when (status) {
            401 -> when (code) {
                "hub.merchant.mfa_required" -> AppErrorException(AppError.MfaRequired(message))
                "hub.merchant.bad_mfa", "hub.merchant.bad_credentials" -> AppErrorException(AppError.Invalid(message))
                else -> AppErrorException(AppError.Unauthorized())
            }
            403 -> AppErrorException(AppError.Forbidden(message))
            404 -> AppErrorException(AppError.NotFound(message))
            400, 409, 422 -> AppErrorException(AppError.Invalid(message))
            429 -> AppErrorException(AppError.RateLimited())
            in 500..599 -> AppErrorException(AppError.Unexpected("PayHub is having trouble right now."))
            else -> AppErrorException(AppError.Unexpected("Request failed (HTTP $status)."))
        }
    }

    /** `(code?, message)` parsed from the server error body, tolerating non-JSON. */
    private fun parseServerMessage(body: String, status: Int): Pair<String?, String> {
        val fallback = "HTTP $status"
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null to fallback
        (root["error"] as? JsonObject)?.let { err ->
            val code = (err["code"])?.scalarString()
            val message = (err["message"])?.scalarString()
            return code to (message ?: code ?: fallback)
        }
        (root["detail"])?.scalarString()?.let { return null to it }
        return null to fallback
    }

    private fun kotlinx.serialization.json.JsonElement.scalarString(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)
            ?.let { runCatching { it.content }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    private fun clamp(v: Int, lo: Int, hi: Int): Int = v.coerceIn(lo, hi)

    // ---------------------------------------------------------------- request bodies

    @Serializable
    private data class DeviceBody(val platform: String = "android", val token: String)

    @Serializable
    private data class TokenBody(val token: String)

    @Serializable
    private data class ChangePasswordBody(
        @SerialName("old_password") val oldPassword: String,
        @SerialName("new_password") val newPassword: String,
        val code: String? = null,
    )

    @Serializable
    private data class CodeBody(val code: String)

    @Serializable
    private data class PasswordBody(val password: String)

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

    // ---------------------------------------------------------------- account / MFA / org / sub-merchants models

    /** Mirrors `app/api/merchant/mfa.py::EnrolOut`. */
    @Serializable
    data class MfaEnrol(
        val secret: String,
        @SerialName("otpauth_uri") val otpauthUri: String,
        val issuer: String = "",
        val account: String = "",
    )

    /** Mirrors `app/api/merchant/org.py::OrgOut`. */
    @Serializable
    data class OrgInfo(
        val id: String,
        val code: String,
        val name: String,
        val type: String,
        val status: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("legal_name") val legalName: String? = null,
        @SerialName("tax_number") val taxNumber: String? = null,
        @SerialName("commercial_register_no") val commercialRegisterNo: String? = null,
        @SerialName("billing_email") val billingEmail: String? = null,
        @SerialName("support_email") val supportEmail: String? = null,
        val phone: String? = null,
        val website: String? = null,
        @SerialName("address_line_1") val addressLine1: String? = null,
        @SerialName("address_line_2") val addressLine2: String? = null,
        val city: String? = null,
        val country: String? = null,
        @SerialName("logo_url") val logoUrl: String? = null,
    )

    /**
     * `PATCH /merchant/org` body. A true partial patch: because [json] has
     * `explicitNulls = false`, only non-null fields are serialised, so the
     * caller sets a field to `""` to clear it (server coerces `""` → `null`),
     * to a new value to change it, or leaves it `null` to omit it.
     */
    @Serializable
    data class OrgPatch(
        val name: String? = null,
        val type: String? = null,
        @SerialName("legal_name") val legalName: String? = null,
        @SerialName("tax_number") val taxNumber: String? = null,
        @SerialName("commercial_register_no") val commercialRegisterNo: String? = null,
        @SerialName("billing_email") val billingEmail: String? = null,
        @SerialName("support_email") val supportEmail: String? = null,
        val phone: String? = null,
        val website: String? = null,
        @SerialName("address_line_1") val addressLine1: String? = null,
        @SerialName("address_line_2") val addressLine2: String? = null,
        val city: String? = null,
        val country: String? = null,
        @SerialName("logo_url") val logoUrl: String? = null,
    )

    /** Mirrors `app/api/merchant/sub_merchants.py::SubMerchantOut`. */
    @Serializable
    data class SubMerchant(
        val id: String,
        @SerialName("merchant_id") val merchantId: String = "",
        val code: String,
        @SerialName("code_prefix") val codePrefix: String,
        val name: String,
        val status: String,
        @SerialName("external_ref") val externalRef: String? = null,
        val metadata: JsonObject = JsonObject(emptyMap()),
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("updated_at") val updatedAt: String = "",
        @SerialName("payments_count") val paymentsCount: Int = 0,
    )

    @Serializable
    data class SubMerchantCreate(
        val code: String,
        @SerialName("code_prefix") val codePrefix: String,
        val name: String,
        val status: String = "active",
        @SerialName("external_ref") val externalRef: String? = null,
    )

    @Serializable
    data class SubMerchantPatch(
        val name: String? = null,
        val status: String? = null,
        @SerialName("external_ref") val externalRef: String? = null,
    )

    /** Mirrors `app/api/merchant/sub_merchants.py::SubUserOut`. */
    @Serializable
    data class SubUser(
        val id: String,
        @SerialName("sub_merchant_id") val subMerchantId: String = "",
        val username: String,
        val role: String,
        val status: String,
        @SerialName("full_name") val fullName: String = "",
        val email: String? = null,
        val mobile: String? = null,
        val phone: String? = null,
        @SerialName("mfa_enabled") val mfaEnabled: Boolean = false,
        @SerialName("last_login_at") val lastLoginAt: String? = null,
        @SerialName("created_at") val createdAt: String = "",
    )

    @Serializable
    data class SubUserCreate(
        val username: String,
        @SerialName("full_name") val fullName: String,
        val email: String? = null,
        val mobile: String? = null,
        val phone: String? = null,
        val role: String = "sub_operator",
    )

    /**
     * `POST .../users` response — a [SubUser] plus the freshly minted invite
     * link. Flat (not extending [SubUser]) for the same reason [PaymentDetail]
     * doesn't extend [PaymentRow].
     */
    @Serializable
    data class SubUserCreated(
        val id: String,
        @SerialName("sub_merchant_id") val subMerchantId: String = "",
        val username: String,
        val role: String,
        val status: String,
        @SerialName("full_name") val fullName: String = "",
        val email: String? = null,
        val mobile: String? = null,
        val phone: String? = null,
        @SerialName("mfa_enabled") val mfaEnabled: Boolean = false,
        @SerialName("last_login_at") val lastLoginAt: String? = null,
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("invite_url") val inviteUrl: String,
        @SerialName("invite_sent_to_channel") val inviteSentToChannel: String? = null,
        @SerialName("invite_expires_at") val inviteExpiresAt: String = "",
    ) {
        fun toRow(): SubUser = SubUser(
            id = id, subMerchantId = subMerchantId, username = username, role = role, status = status,
            fullName = fullName, email = email, mobile = mobile, phone = phone, mfaEnabled = mfaEnabled,
            lastLoginAt = lastLoginAt, createdAt = createdAt,
        )
    }

    @Serializable
    data class SubUserPatch(
        @SerialName("full_name") val fullName: String? = null,
        val email: String? = null,
        val mobile: String? = null,
        val phone: String? = null,
        val role: String? = null,
        val status: String? = null,
    )

    /** `POST .../users/{uid}/reissue-invite` response. */
    @Serializable
    data class ReissueInvite(
        @SerialName("sent_to_channel") val sentToChannel: String? = null,
        @SerialName("invite_url") val inviteUrl: String,
        @SerialName("expires_at") val expiresAt: String = "",
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
