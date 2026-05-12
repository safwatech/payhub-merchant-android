package ly.payhub.merchant

import kotlinx.coroutines.test.runTest
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.AppErrorException
import ly.payhub.merchant.data.appError
import ly.payhub.merchant.data.asAppError
import ly.payhub.merchant.data.runCatchingApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppErrorTest {

    @Test
    fun maps_generic_throwable_to_unexpected() {
        assertTrue(AppError.from(IllegalStateException("boom")) is AppError.Unexpected)
        assertTrue(IOException("x").asAppError() is AppError.Unexpected || IOException("x").asAppError() is AppError.Network)
    }

    @Test
    fun runCatchingApp_success_passes_through() = runTest {
        val r = runCatchingApp { 21 * 2 }
        assertEquals(42, r.getOrNull())
        assertNull(r.appError())
    }

    @Test
    fun runCatchingApp_failure_carries_app_error() = runTest {
        val r = runCatchingApp<Int> { throw IllegalArgumentException("nope") }
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is AppErrorException)
        assertTrue(r.appError() is AppError.Unexpected)
    }

    @Test
    fun app_errors_have_non_blank_messages() {
        listOf(
            AppError.Unauthorized(),
            AppError.Forbidden(),
            AppError.NotFound(),
            AppError.Invalid("bad amount"),
            AppError.MfaRequired(),
            AppError.RateLimited(),
            AppError.Network(),
            AppError.Unexpected(),
        ).forEach { assertTrue(it.message.isNotBlank()) }
    }

    @Test
    fun mfa_required_round_trips_through_app_error_exception() {
        val original = AppError.MfaRequired("need a code")
        val mapped = AppError.from(AppErrorException(original))
        assertTrue(mapped is AppError.MfaRequired)
        assertEquals("need a code", mapped.message)
        // It must NOT be conflated with an auth-loss state.
        assertTrue(mapped !is AppError.Unauthorized)
    }
}
