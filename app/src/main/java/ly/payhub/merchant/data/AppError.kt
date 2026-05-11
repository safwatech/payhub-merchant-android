package ly.payhub.merchant.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ly.payhub.AuthenticationException
import ly.payhub.ConnectionException
import ly.payhub.DecodeException
import ly.payhub.NotFoundException
import ly.payhub.PayhubApiException
import ly.payhub.PayhubException
import ly.payhub.PermissionException
import ly.payhub.RateLimitedException
import ly.payhub.TimeoutException
import ly.payhub.ValidationException

/**
 * UI-facing error type. Repository methods return `Result<T>` whose failure is
 * always an [AppError] — composables never see a raw SDK exception. [message]
 * is short and human; screens render it directly.
 */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {

    /** 401 — the session is gone; the caller should route back to login. */
    data class Unauthorized(override val message: String = "Your session has expired. Please sign in again.") : AppError(message)

    /** 403 — the signed-in role lacks this action. */
    data class Forbidden(override val message: String = "You don't have permission to do that.") : AppError(message)

    /** 404. */
    data class NotFound(override val message: String = "Not found.") : AppError(message)

    /** 422 / 400 — bad input; [message] carries the server's explanation. */
    data class Invalid(override val message: String) : AppError(message)

    /** 429. */
    data class RateLimited(override val message: String = "Too many requests — please slow down and try again.") : AppError(message)

    /** Network unreachable / timeout. */
    data class Network(override val message: String = "Can't reach PayHub. Check your connection and try again.", override val cause: Throwable? = null) : AppError(message, cause)

    /** 5xx, decode errors, anything else. */
    data class Unexpected(override val message: String = "Something went wrong. Please try again.", override val cause: Throwable? = null) : AppError(message, cause)

    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is AppErrorException -> t.error
            is AuthenticationException -> Unauthorized()
            is PermissionException -> Forbidden(friendly(t))
            is NotFoundException -> NotFound(friendly(t))
            is ValidationException -> Invalid(friendly(t))
            is RateLimitedException -> RateLimited()
            is TimeoutException -> Network("The request timed out. Please try again.", t)
            is ConnectionException -> Network(cause = t)
            is DecodeException -> Unexpected("Received an unexpected response from PayHub.", t)
            is PayhubApiException -> if (t.httpStatus in 500..599) {
                Unexpected("PayHub is having trouble right now. Please try again shortly.", t)
            } else {
                Unexpected(friendly(t), t)
            }
            is PayhubException -> Unexpected(cause = t)
            else -> Unexpected(cause = t)
        }

        private fun friendly(t: PayhubApiException): String =
            t.message?.substringBefore(" [request_id=")?.takeIf { it.isNotBlank() } ?: "Request failed."
    }
}

/** Lets us thread an [AppError] through `Result`'s Throwable channel without losing the typed mapping. */
class AppErrorException(val error: AppError) : RuntimeException(error.message, error.cause)

/**
 * Composable-friendly localised message for an [AppError]. The default
 * [AppError.message] is an English fallback used in logs / snackbars where a
 * Composable context isn't available; this extension resolves to the
 * matching `values{,-ar}/strings.xml` entry so UI surfaces translate properly.
 *
 * `Invalid` carries a server-supplied message (the merchant API isn't
 * localised yet) — we surface it verbatim. Same for the `cause`-carrying
 * variants when the server emitted a human string we shouldn't override.
 */
@Composable
fun AppError.localizedMessage(): String = when (this) {
    is AppError.Unauthorized -> stringResource(ly.payhub.merchant.R.string.error_unauthorized)
    is AppError.Forbidden -> stringResource(ly.payhub.merchant.R.string.error_forbidden)
    is AppError.NotFound -> stringResource(ly.payhub.merchant.R.string.error_not_found)
    is AppError.RateLimited -> stringResource(ly.payhub.merchant.R.string.error_rate_limited)
    is AppError.Network -> stringResource(ly.payhub.merchant.R.string.error_network)
    is AppError.Invalid -> message
    is AppError.Unexpected -> stringResource(ly.payhub.merchant.R.string.error_unexpected)
}

/**
 * Wrap a suspending SDK call: on failure the `Result` carries an [AppErrorException].
 * Use [asAppError] / [appError] to read it back.
 */
suspend inline fun <T> runCatchingApp(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (t: Throwable) {
        Result.failure(AppErrorException(AppError.from(t)))
    }

/** The [AppError] behind a failed [Result] produced by [runCatchingApp] (or a best-effort mapping). */
fun Throwable.asAppError(): AppError = (this as? AppErrorException)?.error ?: AppError.from(this)

/** Convenience: the [AppError] of a failed [Result], or null if it succeeded. */
fun <T> Result<T>.appError(): AppError? = exceptionOrNull()?.asAppError()
