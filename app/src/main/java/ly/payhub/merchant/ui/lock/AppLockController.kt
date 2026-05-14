package ly.payhub.merchant.ui.lock

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ly.payhub.merchant.data.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The biometric / device-credential app lock.
 *
 * When the feature is enabled ([isEnabled], persisted in [TokenStore]), the
 * authenticated UI is gated behind a Face ID / fingerprint / device-PIN prompt
 * on cold start and whenever the app returns to the foreground after more than
 * [LOCK_AFTER_MS] in the background. [locked] is only ever `true` while the
 * feature is on, so the UI can show the lock screen on `locked && authenticated`
 * with no extra check.
 *
 * It's a defence-in-depth + UX layer over the (already encrypted-at-rest) token
 * pair — it does not by itself bind the token to the biometric key.
 *
 * The collaborators are passed as plain lambdas so the controller is unit-testable
 * without an Android context; the `@Inject` constructor wires them to [TokenStore]
 * and `SystemClock`, and subscribes to the process lifecycle.
 */
@Singleton
class AppLockController @VisibleForTesting internal constructor(
    private val readEnabled: () -> Boolean,
    private val writeEnabled: (Boolean) -> Unit,
    private val now: () -> Long,
    /**
     * Called after the toggle flips, with the new value. The production wiring
     * routes this to `tokenStore.refreshVault.rewrap(...)` so the refresh
     * token's at-rest form follows the lock state (see [RefreshTokenVault]).
     * Tests pass a no-op.
     */
    private val onLockEnabledChanged: (Boolean) -> Unit = {},
) {
    @Inject
    constructor(tokenStore: TokenStore) : this(
        readEnabled = { tokenStore.appLockEnabled },
        writeEnabled = { tokenStore.appLockEnabled = it },
        now = SystemClock::elapsedRealtime,
        onLockEnabledChanged = { enabled -> tokenStore.refreshVault.rewrap(enabled) },
    ) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = onAppBackgrounded()
            override fun onStart(owner: LifecycleOwner) = onAppForegrounded()
        })
    }

    private val _locked = MutableStateFlow(readEnabled())   // cold start → locked iff the feature is on
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long = Long.MIN_VALUE

    fun onAppBackgrounded() { backgroundedAt = now() }

    fun onAppForegrounded() {
        if (readEnabled() && now() - backgroundedAt > LOCK_AFTER_MS) _locked.value = true
    }

    /** The user just authenticated (the unlock screen, or the enable-confirm prompt). */
    fun markUnlocked() { _locked.value = false }

    /** Flip the feature. Enabling marks unlocked (the caller just authenticated); disabling clears the lock. */
    fun setEnabled(enabled: Boolean) {
        writeEnabled(enabled)
        if (!enabled) _locked.value = false
        // Migrate the refresh-token at-rest wrapping in lockstep with the toggle.
        onLockEnabledChanged(enabled)
    }

    val isEnabled: Boolean get() = readEnabled()

    companion object {
        /** How long the app may sit in the background before a re-lock is required. */
        const val LOCK_AFTER_MS = 2 * 60 * 1000L
    }
}
