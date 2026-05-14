package ly.payhub.merchant.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ly.payhub.CreatePayLinkRequest
import ly.payhub.LoginResult
import ly.payhub.MerchantApiKey
import ly.payhub.MerchantApiKeyWithSecret
import ly.payhub.MerchantDashboard
import ly.payhub.MerchantMe
import ly.payhub.MfaEnrol
import ly.payhub.OrgInfo
import ly.payhub.OrgPatch
import ly.payhub.PayLink
import ly.payhub.PayLinkList
import ly.payhub.PaymentDetail
import ly.payhub.PaymentRow
import ly.payhub.PayhubMerchantClient
import ly.payhub.ReissueInvite
import ly.payhub.SettlementFile
import ly.payhub.SettlementRow
import ly.payhub.SubBreakdownResponse
import ly.payhub.SubMerchant
import ly.payhub.SubMerchantCreate
import ly.payhub.SubMerchantPatch
import ly.payhub.SubUser
import ly.payhub.SubUserCreate
import ly.payhub.SubUserCreated
import ly.payhub.SubUserPatch
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicReference

/**
 * The app's single seam over [PayhubMerchantClient]. It:
 *
 *  - owns the client instance and rebuilds it when the base URL or token pair
 *    changes (login / logout). The client is constructed with the persisted
 *    tokens and an `onTokensRefreshed` callback that writes straight back to
 *    [TokenStore], so the SDK's transparent 401-refresh is durable.
 *  - exposes `suspend` functions returning `Result<T>` whose failure is an
 *    [AppErrorException] (see [runCatchingApp]) — UI never touches a raw SDK type.
 *  - publishes [authState] so the nav host can route between the auth flow and
 *    the home shell reactively.
 *
 * All endpoints (payments, settlements, devices, dashboards, account /
 * org / sub-merchant management) ride the SDK 1.2 namespaces; the SDK's
 * transport coalesces 401 → refresh → retry on its own.
 */
class MerchantRepository(
    private val tokenStore: TokenStore,
    private val sharedHttpClient: OkHttpClient,
    /** Process-lifetime scope (Hilt-provided) for fire-and-forget work like the launch bootstrap. */
    private val appScope: CoroutineScope,
) {
    sealed interface AuthState {
        /** Startup, before the persisted token has been checked. */
        data object Bootstrapping : AuthState
        data object Unauthenticated : AuthState
        data class Authenticated(val me: MerchantMe) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Bootstrapping)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val clientRef = AtomicReference(buildClient())

    val baseUrl: String get() = tokenStore.baseUrl

    /** The cached `MerchantMe`, if signed in. Cheap; refreshed via [refreshMe]. */
    val currentMe: MerchantMe? get() = (_authState.value as? AuthState.Authenticated)?.me

    val isLoggedIn: Boolean get() = client().currentTokens() != null

    private fun client(): PayhubMerchantClient = clientRef.get()

    private fun buildClient(): PayhubMerchantClient {
        val tokens = tokenStore.loadTokens()
        return PayhubMerchantClient(
            baseUrl = tokenStore.baseUrl,
            accessToken = tokens?.accessToken,
            refreshToken = tokens?.refreshToken,
            onTokensRefreshed = { tokenStore.saveTokens(it) },
            httpClient = sharedHttpClient,
        )
    }

    private fun rebuildClient() {
        clientRef.set(buildClient())
    }

    /** Repoint at a different on-prem install. No-op if unchanged. Forces a re-login. */
    fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isEmpty() || normalized == tokenStore.baseUrl) return
        tokenStore.baseUrl = normalized
        tokenStore.clearSession()
        rebuildClient()
        _authState.value = AuthState.Unauthenticated
    }

    // ------------------------------------------------------------------ bootstrap

    /**
     * Launch-time: if a token pair is persisted, verify it with `me()`. Calls
     * [onDone] when the splash screen can be dismissed regardless of outcome.
     * Safe to call from `Activity.onCreate`.
     */
    fun bootstrap(onDone: () -> Unit) {
        if (!isLoggedIn) {
            _authState.value = AuthState.Unauthenticated
            onDone()
            return
        }
        appScope.launch {
            try {
                runCatchingApp { client().auth.me() }.fold(
                    onSuccess = { _authState.value = AuthState.Authenticated(it) },
                    onFailure = {
                        // Bad/expired refresh token → drop the session.
                        if (it.asAppError() is AppError.Unauthorized) handleAuthLoss()
                        else _authState.value = AuthState.Unauthenticated
                    },
                )
            } finally {
                onDone()
            }
        }
    }

    // ------------------------------------------------------------------ auth

    suspend fun login(merchantCode: String, username: String, password: String, subCode: String?): Result<LoginResult> {
        val r = runCatchingApp { client().auth.login(merchantCode, username.trim(), password, subCode?.trim()?.ifBlank { null }) }
        r.onSuccess { lr -> if (!lr.requiresMfa) finishLogin() }
        return r
    }

    suspend fun loginMfa(challengeToken: String, code: String): Result<LoginResult> {
        val r = runCatchingApp { client().auth.loginMfa(challengeToken, code.trim()) }
        r.onSuccess { lr -> if (!lr.requiresMfa) finishLogin() }
        return r
    }

    suspend fun forgotPassword(merchantCode: String, username: String, subCode: String?): Result<Unit> =
        runCatchingApp { client().auth.forgotPassword(merchantCode.trim(), username.trim(), subCode?.trim()?.ifBlank { null }) }

    suspend fun acceptInvite(token: String, newPassword: String): Result<Unit> =
        runCatchingApp { client().auth.acceptInvite(token, newPassword) }

    /** Sign out: revoke the refresh token, wipe local state, drop to unauthenticated. */
    suspend fun logout() {
        runCatching { client().auth.logout() }
        // Best-effort device unregister so a re-login on another account doesn't keep this token.
        // (We don't have the FCM token here without a Firebase call; the More screen handles it.)
        tokenStore.clearSession()
        rebuildClient()
        _authState.value = AuthState.Unauthenticated
    }

    private suspend fun finishLogin() {
        val meResult = runCatchingApp { client().auth.me() }
        _authState.value = meResult.fold(
            onSuccess = { AuthState.Authenticated(it) },
            onFailure = { AuthState.Unauthenticated },
        )
    }

    suspend fun refreshMe(): Result<MerchantMe> {
        val r = runCatchingApp { client().auth.me() }
        r.fold(
            onSuccess = { _authState.value = AuthState.Authenticated(it) },
            onFailure = { if (it.asAppError() is AppError.Unauthorized) handleAuthLoss() },
        )
        return r
    }

    private fun handleAuthLoss() {
        tokenStore.clearSession()
        rebuildClient()
        _authState.value = AuthState.Unauthenticated
    }

    // ------------------------------------------------------------------ pay-links

    suspend fun listPayLinks(
        status: String? = null,
        bucket: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): Result<PayLinkList> = guarded { client().payLinks.list(status = status, bucket = bucket, limit = limit, cursor = cursor) }

    suspend fun getPayLink(id: String): Result<PayLink> = guarded { client().payLinks.get(id) }

    suspend fun createPayLink(request: CreatePayLinkRequest): Result<PayLink> = guarded { client().payLinks.create(request) }

    suspend fun cancelPayLink(id: String): Result<PayLink> = guarded { client().payLinks.cancel(id) }

    suspend fun extendPayLink(id: String, additionalSeconds: Int): Result<PayLink> =
        guarded { client().payLinks.extend(id, additionalSeconds) }

    suspend fun clonePayLink(id: String): Result<PayLink> = guarded { client().payLinks.clone(id) }

    suspend fun markPayLinkShared(id: String): Result<PayLink> = guarded { client().payLinks.markShared(id) }

    // ------------------------------------------------------------------ reports

    suspend fun dashboard(windowHours: Int): Result<MerchantDashboard> = guarded { client().reports.dashboard(windowHours = windowHours) }

    /** Per-shop breakdown for a parent merchant. */
    suspend fun dashboardBySub(windowHours: Int): Result<SubBreakdownResponse> =
        guarded { client().reports.dashboardBySub(windowHours) }

    // ------------------------------------------------------------------ payments (SDK 1.2)

    /** Paginated payments list. Server caps `limit` at 200. */
    suspend fun listPayments(
        psp: String? = null,
        status: String? = null,
        subMerchantId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<PaymentRow>> = guarded {
        client().payments.list(psp = psp, status = status, subMerchantId = subMerchantId, limit = limit, offset = offset)
    }

    /** Payment detail incl. event timeline + metadata. */
    suspend fun getPayment(id: String): Result<PaymentDetail> = guarded {
        client().payments.get(id)
    }

    // ------------------------------------------------------------------ settlements (SDK 1.2)

    suspend fun listSettlements(
        psp: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<SettlementFile>> = guarded {
        client().settlements.list(psp = psp, limit = limit, offset = offset)
    }

    suspend fun getSettlement(fileId: String): Result<SettlementFile> = guarded {
        client().settlements.get(fileId)
    }

    suspend fun listSettlementRows(
        fileId: String,
        statusFilter: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<SettlementRow>> = guarded {
        client().settlements.listRows(fileId, statusFilter = statusFilter, limit = limit, offset = offset)
    }

    // ---- account / org / sub-merchants (SDK 1.2) ----

    suspend fun changePassword(oldPassword: String, newPassword: String, code: String?): Result<Unit> =
        guarded { client().account.changePassword(oldPassword, newPassword, code) }

    suspend fun mfaEnrol(): Result<MfaEnrol> =
        guarded { client().account.mfaEnrol() }

    suspend fun mfaConfirm(code: String): Result<Unit> {
        val r = guarded { client().account.mfaConfirm(code) }
        if (r.isSuccess) refreshMe()
        return r
    }

    suspend fun mfaDisable(password: String): Result<Unit> {
        val r = guarded { client().account.mfaDisable(password) }
        if (r.isSuccess) refreshMe()
        return r
    }

    suspend fun getOrg(): Result<OrgInfo> =
        guarded { client().org.get() }

    suspend fun updateOrg(patch: OrgPatch): Result<OrgInfo> =
        guarded { client().org.update(patch) }

    suspend fun listSubMerchants(): Result<List<SubMerchant>> =
        guarded { client().subMerchants.list() }

    suspend fun getSubMerchant(id: String): Result<SubMerchant> =
        guarded { client().subMerchants.get(id) }

    suspend fun createSubMerchant(body: SubMerchantCreate): Result<SubMerchant> =
        guarded { client().subMerchants.create(body) }

    suspend fun updateSubMerchant(id: String, body: SubMerchantPatch): Result<SubMerchant> =
        guarded { client().subMerchants.update(id, body) }

    suspend fun deleteSubMerchant(id: String): Result<Unit> =
        guarded { client().subMerchants.delete(id) }

    suspend fun listSubUsers(subId: String): Result<List<SubUser>> =
        guarded { client().subMerchants.users.list(subId) }

    suspend fun createSubUser(subId: String, body: SubUserCreate): Result<SubUserCreated> =
        guarded { client().subMerchants.users.create(subId, body) }

    suspend fun updateSubUser(
        subId: String,
        uid: String,
        body: SubUserPatch,
    ): Result<SubUser> =
        guarded { client().subMerchants.users.update(subId, uid, body) }

    suspend fun disableSubUser(subId: String, uid: String): Result<Unit> =
        guarded { client().subMerchants.users.disable(subId, uid) }

    suspend fun reissueSubUserInvite(subId: String, uid: String): Result<ReissueInvite> =
        guarded { client().subMerchants.users.reissueInvite(subId, uid) }

    suspend fun clearSubUserMfa(subId: String, uid: String, code: String): Result<Unit> =
        guarded { client().subMerchants.users.clearMfa(subId, uid, code) }

    // ---- sub-merchant API keys (sub-scoped only — parent keys are web-portal-only) ----

    suspend fun listSubMerchantApiKeys(subId: String): Result<List<MerchantApiKey>> =
        guarded { client().subMerchants.apiKeys.list(subId) }

    /**
     * Mint a new key. The returned [MerchantApiKeyWithSecret.secret] is the
     * plaintext secret — server stores only an argon2 hash, so the caller
     * MUST surface it to the operator immediately (the screen does this in a
     * copy-or-lose-it modal) and discard it once the modal closes.
     */
    suspend fun createSubMerchantApiKey(
        subId: String,
        scopes: List<String>,
        allowedIps: List<String> = emptyList(),
        rateLimitTier: String = "standard",
    ): Result<MerchantApiKeyWithSecret> = guarded {
        client().subMerchants.apiKeys.create(subId, scopes, allowedIps, rateLimitTier)
    }

    suspend fun revokeSubMerchantApiKey(subId: String, keyId: String): Result<MerchantApiKey> =
        guarded { client().subMerchants.apiKeys.revoke(subId, keyId) }

    // ------------------------------------------------------------------ devices (push)

    suspend fun registerDevice(fcmToken: String): Result<Unit> =
        guarded { client().devices.registerAndroid(fcmToken); Unit }

    /** Best-effort: signed-out callers get a silent success — we have nothing to unregister with. */
    suspend fun unregisterDevice(fcmToken: String): Result<Unit> {
        if (client().currentTokens()?.accessToken == null) return Result.success(Unit)
        return guarded { client().devices.unregister(fcmToken) }
    }

    var pushEnabled: Boolean
        get() = tokenStore.pushEnabled
        set(value) { tokenStore.pushEnabled = value }

    // ------------------------------------------------------------------ internals

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> {
        val r = runCatchingApp { block() }
        propagateAuthLoss(r)
        return r
    }

    private fun <T> propagateAuthLoss(r: Result<T>) {
        if (r.appError() is AppError.Unauthorized) handleAuthLoss()
    }
}
