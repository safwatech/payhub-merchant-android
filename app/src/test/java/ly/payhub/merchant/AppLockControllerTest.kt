package ly.payhub.merchant

import ly.payhub.merchant.ui.lock.AppLockController
import ly.payhub.merchant.ui.lock.AppLockController.Companion.LOCK_AFTER_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The background-timeout / cold-start re-lock policy in [AppLockController]. */
class AppLockControllerTest {

    private class Harness(enabledInitially: Boolean) {
        var enabled = enabledInitially
        var clock = 1_000L
        val controller = AppLockController(
            readEnabled = { enabled },
            writeEnabled = { enabled = it },
            now = { clock },
        )
    }

    @Test
    fun disabled_means_never_locked_on_cold_start() {
        val h = Harness(enabledInitially = false)
        assertFalse(h.controller.locked.value)
    }

    @Test
    fun enabled_means_locked_on_cold_start() {
        val h = Harness(enabledInitially = true)
        assertTrue(h.controller.locked.value)
    }

    @Test
    fun unlock_then_short_background_does_not_relock() {
        val h = Harness(enabledInitially = true)
        h.controller.markUnlocked()
        assertFalse(h.controller.locked.value)

        h.controller.onAppBackgrounded()
        h.clock += LOCK_AFTER_MS / 2          // back within the grace window (e.g. a glance at another app)
        h.controller.onAppForegrounded()
        assertFalse(h.controller.locked.value)
    }

    @Test
    fun unlock_then_long_background_relocks() {
        val h = Harness(enabledInitially = true)
        h.controller.markUnlocked()

        h.controller.onAppBackgrounded()
        h.clock += LOCK_AFTER_MS + 1
        h.controller.onAppForegrounded()
        assertTrue(h.controller.locked.value)
    }

    @Test
    fun disabled_session_never_relocks_however_long_it_was_backgrounded() {
        val h = Harness(enabledInitially = false)
        h.controller.onAppBackgrounded()
        h.clock += LOCK_AFTER_MS * 100
        h.controller.onAppForegrounded()
        assertFalse(h.controller.locked.value)
    }

    @Test
    fun enabling_marks_unlocked_disabling_clears_the_lock() {
        val h = Harness(enabledInitially = true)            // cold-start → locked
        assertTrue(h.controller.locked.value)

        h.controller.setEnabled(false)                       // turning it off clears the lock
        assertFalse(h.controller.locked.value)
        assertFalse(h.enabled)

        h.controller.setEnabled(true)                        // turning it on is a fresh unlock — no immediate lock
        assertFalse(h.controller.locked.value)
        assertTrue(h.enabled)
    }
}
