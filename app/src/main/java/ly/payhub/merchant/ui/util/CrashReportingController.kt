package ly.payhub.merchant.ui.util

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import ly.payhub.merchant.BuildConfig

/**
 * Runtime-gated crash reporting (Sentry / GlitchTip protocol).
 *
 * Off by default. [apply] is the single entry point: it fires
 * [SentryAndroid.init] when both gates are open — the toggle is on **and** a
 * non-blank build-time DSN is baked in — and [Sentry.close]s the SDK when
 * either gate flips off. No PII: `tracesSampleRate = 0`, breadcrumbs / user
 * tracing disabled, release tag is `payhub-merchant-android@<versionName>`.
 *
 * The controller is dependency-light so it stays unit-testable with mockk
 * static stubs of `SentryAndroid` / `Sentry`; the host wires it from
 * [PayhubMerchantApp.onCreate] and re-applies it whenever
 * `tokenStore.crashReportingFlow` emits.
 *
 * See `docs/superpowers/specs/2026-05-14-d6-d7-sdk-1.2-and-app-followups-design.md` §3.4.
 */
class CrashReportingController(
    private val application: Application,
    private val dsn: String = BuildConfig.SENTRY_DSN,
) {
    /** Re-evaluate the gates and start or stop Sentry accordingly. */
    fun apply(enabled: Boolean) {
        if (dsn.isBlank()) return  // build never carried a DSN — no-op forever.
        if (enabled) {
            SentryAndroid.init(application) { options ->
                options.dsn = dsn
                options.release = "payhub-merchant-android@${BuildConfig.VERSION_NAME}"
                options.environment = if (BuildConfig.DEBUG) "debug" else "release"
                options.tracesSampleRate = 0.0
                options.isEnableAutoSessionTracking = false
                options.isSendDefaultPii = false
                options.isAttachThreads = false
                options.isAttachStacktrace = true
            }
        } else if (Sentry.isEnabled()) {
            Sentry.close()
        }
    }
}
