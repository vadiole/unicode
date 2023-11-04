package vadiole.unicode.utils.extension

import android.content.Context
import android.graphics.Paint
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.Px
import vadiole.unicode.R

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

inline var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

@Suppress("NOTHING_TO_INLINE")
inline fun View.setPadding(@Px size: Int) {
    setPadding(size, size, size, size)
}

@Suppress("NOTHING_TO_INLINE")
inline fun View.setPaddingHorizontal(@Px size: Int) {
    setPadding(size, paddingTop, size, paddingBottom)
}

@Suppress("NOTHING_TO_INLINE")
inline fun View.hideKeyboard() {
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

fun View.getDividerPaint() = Paint().apply {
    isAntiAlias = false
    strokeWidth = 1f
    color = context.getColor(R.color.windowDivider)
}
