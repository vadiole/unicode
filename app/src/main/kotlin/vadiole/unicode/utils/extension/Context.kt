package vadiole.unicode.utils.extension

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_TEXT

fun Context.toClipboard(label: CharSequence, text: CharSequence) {
    val systemService = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val myClip = ClipData.newPlainText(label, text)
    systemService.setPrimaryClip(myClip)
}

fun Context.share(text: CharSequence) {
    val intent = Intent(ACTION_SEND)
        .putExtra(EXTRA_TEXT, text)
        .setType("text/plain")
    startActivity(intent)
}