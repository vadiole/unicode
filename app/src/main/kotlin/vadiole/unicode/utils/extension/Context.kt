package vadiole.unicode.utils.extension

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT
import android.util.Log

fun Context.toClipboard(label: CharSequence, text: CharSequence) {
    try {
        val systemService = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val myClip = ClipData.newPlainText(label, text)
        systemService.setPrimaryClip(myClip)
    } catch (e: Exception) {
        // Sometimes doesn't work
        Log.e("Clipboard", "Failed to copy to clipboard", e)
    }
}

fun Context.share(text: CharSequence) {
    val intent = Intent(ACTION_SEND)
        .putExtra(EXTRA_TEXT, text)
        .setType("text/plain")
    startActivity(intent)
}