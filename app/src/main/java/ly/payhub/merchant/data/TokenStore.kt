package ly.payhub.merchant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ly.payhub.PayhubClient
import ly.payhub.TokenPair

/**
 * Tiny encrypted key/value store for the merchant session: the bearer/refresh
 * token pair, the configured base URL, and the push-notifications opt-in flag.
 *
 * Backed by [EncryptedSharedPreferences] (AES-256-GCM, key in the Android
 * Keystore). It is loaded synchronously at startup — it holds only a handful
 * of short strings, so blocking the main thread once is acceptable. If the
 * keystore-backed prefs ever fail to open (corruption, OS migration), we fall
 * back to a wiped, plain in-memory-ish prefs file so the app still starts —
 * the user is simply asked to sign in again.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.deleteSharedPreferences(FILE_NAME)
        context.getSharedPreferences("${FILE_NAME}_fallback", Context.MODE_PRIVATE)
    }

    // ---- tokens ----

    fun loadTokens(): TokenPair? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return TokenPair(accessToken = access, refreshToken = refresh, expiresIn = 0)
    }

    fun saveTokens(tokens: TokenPair) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .apply()
    }

    fun clearTokens() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    // ---- base URL ----

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: PayhubClient.DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()
        }

    // ---- push opt-in ----

    var pushEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_PUSH_ENABLED, value).apply() }

    // ---- biometric app lock opt-in ----

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) { prefs.edit().putBoolean(KEY_APP_LOCK, value).apply() }

    // ---- crash-reporting opt-in ----

    /**
     * Whether the user has opted in to anonymous crash reporting. Off by
     * default; cleared on sign-out so a re-login starts fresh. Exposed as a
     * [StateFlow] so [PayhubMerchantApp]'s collector can flip Sentry on/off
     * the moment the user toggles it in Diagnostics.
     */
    var crashReportingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CRASH_REPORTING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CRASH_REPORTING, value).apply()
            _crashReportingFlow.value = value
        }

    private val _crashReportingFlow = MutableStateFlow(crashReportingEnabled)
    val crashReportingFlow: StateFlow<Boolean> = _crashReportingFlow.asStateFlow()

    fun clearSession() {
        // Wipe credentials but keep the base URL — a re-login on the same install
        // shouldn't have to re-type the server.
        clearTokens()
        prefs.edit()
            .remove(KEY_PUSH_ENABLED)
            .remove(KEY_APP_LOCK)
            .remove(KEY_CRASH_REPORTING)
            .apply()
        _crashReportingFlow.value = false
    }

    private companion object {
        const val FILE_NAME = "payhub_merchant_secure_prefs"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_BASE_URL = "base_url"
        const val KEY_PUSH_ENABLED = "push_enabled"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_CRASH_REPORTING = "crash_reporting_enabled"
    }
}
