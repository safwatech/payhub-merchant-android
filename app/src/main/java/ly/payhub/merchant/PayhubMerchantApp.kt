package ly.payhub.merchant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ly.payhub.merchant.data.TokenStore
import ly.payhub.merchant.di.AppCoroutineScope
import ly.payhub.merchant.ui.util.CrashReportingController
import javax.inject.Inject

@HiltAndroidApp
class PayhubMerchantApp : Application() {

    @Inject lateinit var tokenStore: TokenStore

    @Inject @AppCoroutineScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        initCrashReporting()
        createPaymentsChannel()
    }

    /**
     * Crash / error reporting → GlitchTip (Sentry protocol). **Off by
     * default**; we only init when both gates are open — a non-blank
     * [BuildConfig.SENTRY_DSN] **and** the user's opt-in toggle in
     * Diagnostics. [CrashReportingController.apply] is idempotent, so the
     * collector below also flips Sentry on/off the moment the toggle
     * changes without an app restart. No PII; release-tagged
     * `payhub-merchant-android@<versionName>`.
     */
    private fun initCrashReporting() {
        val controller = CrashReportingController(this, BuildConfig.SENTRY_DSN)
        controller.apply(tokenStore.crashReportingEnabled)
        appScope.launch {
            tokenStore.crashReportingFlow.collect { controller.apply(it) }
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
