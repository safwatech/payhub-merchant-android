package ly.payhub.merchant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PayhubMerchantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createPaymentsChannel()
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
