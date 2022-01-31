package vadiole.unicode.utils.ktx

import android.graphics.Rect
import android.view.View
import android.widget.TextView

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

inline fun <T : View> T?.onSizeChange(crossinline runnable: T.() -> Unit) = this?.apply {
    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val rect = Rect(left, top, right, bottom)
        val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
        if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
            runnable(this)
        }
    }
}

fun View.Text(init: TextView.() -> Unit): TextView {
    return TextView(context).apply(init)
}

