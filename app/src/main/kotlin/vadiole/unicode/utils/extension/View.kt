package vadiole.unicode.utils.extension

import android.view.View
import androidx.annotation.Px

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
    setPadding(size, paddingTop, size, paddingTop)
}