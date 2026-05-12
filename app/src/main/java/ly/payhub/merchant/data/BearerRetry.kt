package ly.payhub.merchant.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The "transparent 401 → refresh → retry once" policy for the raw-HTTP shim
 * ([RawMerchantApi]) calls. The `payhub-android` SDK does this internally for the
 * endpoints it covers; this gives the raw shim the same behaviour so an
 * access-token expiry mid-session doesn't bounce the user to login while the
 * 30-day refresh token is still good.
 *
 * Kept Android-free and dependency-light so it's directly unit-testable;
 * [MerchantRepository.withAccess] wires it to the SDK client + [TokenStore].
 *
 * @param currentAccessToken the live access token, or null when there isn't one
 * @param refresh runs a one-shot bearer refresh and returns the new access token,
 *        or null if the refresh token is also dead. Invoked at most once per burst
 *        of concurrent 401s (it's serialised behind a mutex) — and skipped entirely
 *        if another caller already refreshed under the lock.
 * @param onAuthLoss invoked once when there's no token to begin with, or the
 *        refresh failed, or a 401 survives the retry
 */
class BearerRetry(
    private val currentAccessToken: () -> String?,
    private val refresh: suspend () -> String?,
    private val onAuthLoss: () -> Unit,
) {
    private val refreshMutex = Mutex()

    /**
     * Run [block] with the current bearer token; on a 401, refresh once and retry.
     * [block] receives the token to use and returns a `Result` whose failure (if
     * any) is an [AppErrorException] — exactly what [runCatchingApp] produces.
     */
    suspend fun <T> execute(block: suspend (token: String) -> Result<T>): Result<T> {
        val token = currentAccessToken()
        if (token == null) {
            onAuthLoss()
            return Result.failure(AppErrorException(AppError.Unauthorized()))
        }
        val first = block(token)
        if (first.appError() !is AppError.Unauthorized) return first
        val fresh = refreshOnce(stale = token)
        if (fresh == null) {
            onAuthLoss()
            return first
        }
        val second = block(fresh)
        if (second.appError() is AppError.Unauthorized) onAuthLoss()
        return second
    }

    /** Refresh, de-duped: a caller whose token already changed under the lock reuses it. */
    private suspend fun refreshOnce(stale: String): String? = refreshMutex.withLock {
        val cur = currentAccessToken()
        if (cur != null && cur != stale) cur else refresh()
    }
}
