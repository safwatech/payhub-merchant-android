package ly.payhub.merchant.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** Fire the system share sheet with a payment-link URL (plain text). */
fun Context.shareText(text: String, chooserTitle: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Copy a string to the clipboard. On API 33+ the OS shows its own confirmation toast. */
fun Context.copyToClipboard(label: String, text: String): Boolean {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    // Callers should still show a snackbar on API < 33 (no system feedback there).
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
