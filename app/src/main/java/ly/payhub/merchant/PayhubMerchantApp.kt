package ly.payhub.merchant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class PayhubMerchantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initCrashReporting()
        createPaymentsChannel()
    }

    /**
     * Crash / error reporting → GlitchTip (Sentry protocol). The DSN is a build-time
     * constant ([BuildConfig.SENTRY_DSN]); empty by default ⇒ we skip init entirely
     * (no SDK, no network) — the vendor sets `-Ppayhub.sentryDsn=…` or PAYHUB_SENTRY_DSN
     * to enable it. No PII: no user identity, no HTTP-breadcrumb interceptor wired,
     * crashes/errors only (no performance tracing).
     */
    private fun initCrashReporting() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.release = "payhub-merchant-android@${BuildConfig.VERSION_NAME}"
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.tracesSampleRate = 0.0
            options.isEnableAutoSessionTracking = true
            options.isSendDefaultPii = false
        }
    }

    private fun createPaymentsChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            getString(R.string.notif_channel_payments_id),
            getString(R.string.notif_channel_payments_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.notif_channel_payments_desc) }
        mgr.createNotificationChannel(channel)
    }
}
