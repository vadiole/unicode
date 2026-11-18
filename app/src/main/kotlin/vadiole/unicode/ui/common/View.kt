package vadiole.unicode.ui.common

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager

private var enabled: Boolean = true
private val ENABLE_AGAIN = object : Runnable {
    override fun run() {
        enabled = true
    }
}

var <T : View> T.onClick: T.() -> Unit
    @Deprecated("There is no getter for onClick")
    get() = throw Exception()
    set(action) = setOnClickListener { view ->
        if (enabled) {
            enabled = false
            view.post(ENABLE_AGAIN)
            @Suppress("UNCHECKED_CAST")
            action.invoke(view as T)
        }
    }

var <T : View> T.onLongClick: T.() -> Unit
    @Deprecated("There is no getter for onLongClick")
    get() = throw Exception()
    set(action) = setOnLongClickListener { view ->
        @Suppress("UNCHECKED_CAST")
        action.invoke(view as T)
        true
    }

fun View.hideKeyboard() {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!imm.isActive) {
            return
        }
        imm.hideSoftInputFromWindow(windowToken, 0)
    } catch (e: Exception) {
        Log.e("KEYBOARD", "hideKeyboard failed:", e)
    }
}

val View.showsLayoutBounds: Boolean
    get() = Build.VERSION.SDK_INT >= 30 && isShowingLayoutBounds
