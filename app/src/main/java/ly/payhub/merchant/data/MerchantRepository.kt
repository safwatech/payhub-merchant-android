package ly.payhub.merchant.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ly.payhub.CreatePayLinkRequest
import ly.payhub.LoginResult
import ly.payhub.MerchantDashboard
import ly.payhub.MerchantMe
import ly.payhub.PayLink
import ly.payhub.PayLinkList
import ly.payhub.PayhubMerchantClient
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
 * For `/merchant/devices` (push registration) and the per-shop dashboard
 * breakdown it delegates to [RawMerchantApi] — not in SDK 1.1.0.
 */
class MerchantRepository(
    private val tokenStore: TokenStore,
    private val rawApi: RawMerchantApi,
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

    /** Per-shop breakdown for a parent merchant. Raw HTTP — see [RawMerchantApi]. */
    suspend fun dashboardBySub(windowHours: Int): Result<RawMerchantApi.SubBreakdownResponse> =
        withAccess { token -> rawApi.dashboardBySub(tokenStore.baseUrl, token, windowHours) }

    // ------------------------------------------------------------------ payments (raw — SDK 1.2)

    /** Paginated payments list. Server caps `limit` at 200. */
    suspend fun listPayments(
        psp: String? = null,
        status: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<RawMerchantApi.PaymentRow>> = withAccess { token ->
        rawApi.listPayments(tokenStore.baseUrl, token, psp = psp, status = status, limit = limit, offset = offset)
    }

    /** Payment detail incl. event timeline + metadata. */
    suspend fun getPayment(id: String): Result<RawMerchantApi.PaymentDetail> = withAccess { token ->
        rawApi.getPayment(tokenStore.baseUrl, token, id)
    }

    // ------------------------------------------------------------------ settlements (raw — SDK 1.2)

    suspend fun listSettlements(
        psp: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<RawMerchantApi.SettlementFile>> = withAccess { token ->
        rawApi.listSettlements(tokenStore.baseUrl, token, psp = psp, limit = limit, offset = offset)
    }

    suspend fun getSettlement(fileId: String): Result<RawMerchantApi.SettlementFile> = withAccess { token ->
        rawApi.getSettlement(tokenStore.baseUrl, token, fileId)
    }

    suspend fun listSettlementRows(
        fileId: String,
        statusFilter: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<RawMerchantApi.SettlementRow>> = withAccess { token ->
        rawApi.listSettlementRows(tokenStore.baseUrl, token, fileId, statusFilter = statusFilter, limit = limit, offset = offset)
    }

    // ---- account / org / sub-merchants (raw — SDK 1.2) ----

    suspend fun changePassword(oldPassword: String, newPassword: String, code: String?): Result<Unit> =
        withAccess { token -> rawApi.changePassword(tokenStore.baseUrl, token, oldPassword, newPassword, code) }

    suspend fun mfaEnrol(): Result<RawMerchantApi.MfaEnrol> =
        withAccess { token -> rawApi.mfaEnrol(tokenStore.baseUrl, token) }

    suspend fun mfaConfirm(code: String): Result<Unit> {
        val r = withAccess { token -> rawApi.mfaConfirm(tokenStore.baseUrl, token, code) }
        if (r.isSuccess) refreshMe()
        return r
    }

    suspend fun mfaDisable(password: String): Result<Unit> {
        val r = withAccess { token -> rawApi.mfaDisable(tokenStore.baseUrl, token, password) }
        if (r.isSuccess) refreshMe()
        return r
    }

    suspend fun getOrg(): Result<RawMerchantApi.OrgInfo> =
        withAccess { token -> rawApi.getOrg(tokenStore.baseUrl, token) }

    suspend fun updateOrg(patch: RawMerchantApi.OrgPatch): Result<RawMerchantApi.OrgInfo> =
        withAccess { token -> rawApi.updateOrg(tokenStore.baseUrl, token, patch) }

    suspend fun listSubMerchants(): Result<List<RawMerchantApi.SubMerchant>> =
        withAccess { token -> rawApi.listSubMerchants(tokenStore.baseUrl, token) }

    suspend fun getSubMerchant(id: String): Result<RawMerchantApi.SubMerchant> =
        withAccess { token -> rawApi.getSubMerchant(tokenStore.baseUrl, token, id) }

    suspend fun createSubMerchant(body: RawMerchantApi.SubMerchantCreate): Result<RawMerchantApi.SubMerchant> =
        withAccess { token -> rawApi.createSubMerchant(tokenStore.baseUrl, token, body) }

    suspend fun updateSubMerchant(id: String, body: RawMerchantApi.SubMerchantPatch): Result<RawMerchantApi.SubMerchant> =
        withAccess { token -> rawApi.updateSubMerchant(tokenStore.baseUrl, token, id, body) }

    suspend fun deleteSubMerchant(id: String): Result<Unit> =
        withAccess { token -> rawApi.deleteSubMerchant(tokenStore.baseUrl, token, id) }

    suspend fun listSubUsers(subId: String): Result<List<RawMerchantApi.SubUser>> =
        withAccess { token -> rawApi.listSubUsers(tokenStore.baseUrl, token, subId) }

    suspend fun createSubUser(subId: String, body: RawMerchantApi.SubUserCreate): Result<RawMerchantApi.SubUserCreated> =
        withAccess { token -> rawApi.createSubUser(tokenStore.baseUrl, token, subId, body) }

    suspend fun updateSubUser(
        subId: String,
        uid: String,
        body: RawMerchantApi.SubUserPatch,
    ): Result<RawMerchantApi.SubUser> =
        withAccess { token -> rawApi.updateSubUser(tokenStore.baseUrl, token, subId, uid, body) }

    suspend fun disableSubUser(subId: String, uid: String): Result<Unit> =
        withAccess { token -> rawApi.disableSubUser(tokenStore.baseUrl, token, subId, uid) }

    suspend fun reissueSubUserInvite(subId: String, uid: String): Result<RawMerchantApi.ReissueInvite> =
        withAccess { token -> rawApi.reissueSubUserInvite(tokenStore.baseUrl, token, subId, uid) }

    suspend fun clearSubUserMfa(subId: String, uid: String, code: String): Result<Unit> =
        withAccess { token -> rawApi.clearSubUserMfa(tokenStore.baseUrl, token, subId, uid, code) }

    // ------------------------------------------------------------------ devices (push)

    suspend fun registerDevice(fcmToken: String): Result<Unit> =
        withAccess { token -> rawApi.registerDevice(tokenStore.baseUrl, token, fcmToken) }

    /** Best-effort: signed-out callers get a silent success — we have nothing to unregister with. */
    suspend fun unregisterDevice(fcmToken: String): Result<Unit> {
        val token = client().currentTokens()?.accessToken ?: return Result.success(Unit)
        return rawApi.unregisterDevice(tokenStore.baseUrl, token, fcmToken).also { propagateAuthLoss(it) }
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

    /**
     * Wraps a raw-API call that needs the current bearer token. Mirrors [guarded]
     * for the SDK path: passes the access token in (returning Unauthorized
     * straight away if we don't have one) and trips [handleAuthLoss] on a 401.
     */
    private suspend fun <T> withAccess(block: suspend (String) -> Result<T>): Result<T> {
        val token = client().currentTokens()?.accessToken
            ?: return Result.failure(AppErrorException(AppError.Unauthorized()))
        val r = block(token)
        propagateAuthLoss(r)
        return r
    }

    private fun <T> propagateAuthLoss(r: Result<T>) {
        if (r.appError() is AppError.Unauthorized) handleAuthLoss()
    }
}
