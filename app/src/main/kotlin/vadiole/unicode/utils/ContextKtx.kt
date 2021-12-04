package vadiole.unicode.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.view.View
import android.widget.Toast
import vadiole.unicode.BuildConfig

fun Context.toClipboard(label: String, text: String) {
    val systemService = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val myClip = ClipData.newPlainText(label, text)
    systemService.setPrimaryClip(myClip)
}

fun View.toast(message: String, debug: Boolean = false, long: Boolean = false) {
    if (debug && !BuildConfig.DEBUG) return
    Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}