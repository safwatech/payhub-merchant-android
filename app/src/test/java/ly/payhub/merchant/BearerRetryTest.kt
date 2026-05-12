package ly.payhub.merchant

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.AppErrorException
import ly.payhub.merchant.data.BearerRetry
import ly.payhub.merchant.data.appError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The transparent "401 → refresh → retry once" policy the raw-HTTP shim rides
 * (see [BearerRetry] / [ly.payhub.merchant.data.MerchantRepository.withAccess]).
 */
class BearerRetryTest {

    private fun unauthorized(): Result<String> = Result.failure(AppErrorException(AppError.Unauthorized()))
    private fun invalid(msg: String): Result<String> = Result.failure(AppErrorException(AppError.Invalid(msg)))
    private fun mustNotRun(): Nothing = throw AssertionError("should not have been called")

    @Test
    fun no_token_is_unauthorized_and_signals_auth_loss() = runTest {
        var authLost = 0
        val retry = BearerRetry({ null }, { mustNotRun() }, { authLost++ })
        val r = retry.execute<String> { mustNotRun() }
        assertTrue(r.appError() is AppError.Unauthorized)
        assertEquals(1, authLost)
    }

    @Test
    fun success_first_try_does_not_refresh() = runTest {
        var refreshed = 0
        val retry = BearerRetry({ "tok-1" }, { refreshed++; "tok-2" }, { mustNotRun() })
        var seen: String? = null
        val r = retry.execute { token -> seen = token; Result.success("ok") }
        assertEquals("ok", r.getOrNull())
        assertEquals("tok-1", seen)
        assertEquals(0, refreshed)
    }

    @Test
    fun non_401_failure_propagates_without_refresh() = runTest {
        var refreshed = 0
        val retry = BearerRetry({ "tok-1" }, { refreshed++; "tok-2" }, { mustNotRun() })
        val r = retry.execute { invalid("nope") }
        assertEquals("nope", (r.appError() as AppError.Invalid).message)
        assertEquals(0, refreshed)
    }

    @Test
    fun on_401_refreshes_once_and_retries_with_the_new_token() = runTest {
        var current = "tok-1"
        val retry = BearerRetry({ current }, { current = "tok-2"; current }, { mustNotRun() })
        val tokensTried = mutableListOf<String>()
        val r = retry.execute { token ->
            tokensTried += token
            if (token == "tok-1") unauthorized() else Result.success("ok-with-$token")
        }
        assertEquals("ok-with-tok-2", r.getOrNull())
        assertEquals(listOf("tok-1", "tok-2"), tokensTried)
    }

    @Test
    fun refresh_returning_null_signals_auth_loss_and_surfaces_the_original_401() = runTest {
        var authLost = 0
        val retry = BearerRetry({ "tok-1" }, { null }, { authLost++ })
        val r = retry.execute { unauthorized() }
        assertTrue(r.appError() is AppError.Unauthorized)
        assertEquals(1, authLost)
    }

    @Test
    fun a_401_that_survives_the_retry_signals_auth_loss() = runTest {
        var authLost = 0
        var current = "tok-1"
        val retry = BearerRetry({ current }, { current = "tok-2"; current }, { authLost++ })
        val r = retry.execute { unauthorized() }   // still 401 even with tok-2
        assertTrue(r.appError() is AppError.Unauthorized)
        assertEquals(1, authLost)
    }

    @Test
    fun concurrent_401s_refresh_the_token_only_once() = runTest {
        val refreshCalls = AtomicInteger(0)
        var current = "tok-1"
        val retry = BearerRetry(
            currentAccessToken = { current },
            refresh = {
                refreshCalls.incrementAndGet()
                delay(10)                 // hold the refresh mutex while the other callers pile up
                current = "tok-2"
                current
            },
            onAuthLoss = { mustNotRun() },
        )
        val results = (1..8).map {
            async {
                retry.execute { token -> if (token == "tok-1") unauthorized() else Result.success("ok") }
            }
        }.awaitAll()
        assertTrue(results.all { it.getOrNull() == "ok" })
        assertEquals(1, refreshCalls.get())
    }
}
