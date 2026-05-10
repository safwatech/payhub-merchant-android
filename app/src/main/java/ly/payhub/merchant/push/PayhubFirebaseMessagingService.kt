package ly.payhub.merchant.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ly.payhub.merchant.MainActivity
import ly.payhub.merchant.R
import ly.payhub.merchant.data.MerchantRepository
import javax.inject.Inject
import kotlin.random.Random

/**
 * Receives FCM messages from PayHub.
 *
 *  - [onNewToken]: if a session exists, (re)register the token with the hub via
 *    [MerchantRepository.registerDevice]. (If the user never enabled push, the
 *    server can simply ignore an unwanted registration — but we still gate on
 *    `repo.pushEnabled` to be polite.)
 *  - [onMessageReceived]: build a notification on the "payments" channel. A
 *    `pay_link_id` data field deep-links the tap into the app.
 *
 * The whole thing degrades to nothing if Google Play services / a Firebase
 * project isn't present — `assembleDebug` on a fresh clone (no
 * `google-services.json`) won't apply the Google-services plugin, in which case
 * Firebase initialisation is a runtime no-op and this service never fires.
 */
@AndroidEntryPoint
class PayhubFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var repository: MerchantRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (!repository.isLoggedIn || !repository.pushEnabled) return
        scope.launch { repository.registerDevice(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "PayHub"
        val body = message.notification?.body ?: data["body"] ?: "You have a new payment update."
        val payLinkId = data["pay_link_id"]
        showNotification(title, body, payLinkId)
    }

    private fun showNotification(title: String, body: String, payLinkId: String?) {
        val channelId = getString(R.string.notif_channel_payments_id)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (payLinkId != null) {
                // Reuse the app's own scheme; AppNavHost can pick this up if extended.
                action = Intent.ACTION_VIEW
                this.data = Uri.parse("payhub://pay-link/$payLinkId")
            }
        }
        val pending = PendingIntent.getActivity(
            this,
            payLinkId?.hashCode() ?: 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        mgr.notify(Random.nextInt(), notification)
    }
}
