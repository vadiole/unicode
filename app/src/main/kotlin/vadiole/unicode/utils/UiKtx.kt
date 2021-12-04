package vadiole.unicode.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

const val fill = ViewGroup.LayoutParams.MATCH_PARENT
const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

fun Float.dp(context: Context) = this * context.resources.displayMetrics.density

fun Int.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()

val WindowInsetsCompat.navigationBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.navigationBars())

val WindowInsetsCompat.statusBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.statusBars())


private var enabled = AtomicBoolean(true)
private val ENABLE_AGAIN = { enabled.set(true) }

@Suppress("UNCHECKED_CAST")
var <T : View> T.onClick: T.() -> Unit
    get() = throw RuntimeException("There is no getter for onClick")
    set(action) = setOnClickListener { view ->
        if (enabled.getAndSet(false)) {
            view.post(ENABLE_AGAIN)
            action.invoke(view as T)
        }
    }

@Suppress("UNCHECKED_CAST")
var <T : View> T.onLongClick: T.() -> Unit
    get() = throw RuntimeException("There is no getter for onLongClick")
    set(action) = setOnLongClickListener { view ->
        action.invoke(view as T)
        true
    }

fun frame(
    width: Int,
    height: Int,
    gravity: Int = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0
): FrameLayout.LayoutParams {
    val margins = Rect(marginLeft, marginTop, marginRight, marginBottom)
    return frame(width, height, gravity, margins)
}

fun frame(
    width: Int,
    height: Int,
    gravity: Int = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY,
    margins: Rect = Rect()
): FrameLayout.LayoutParams {
    val layoutParams = FrameLayout.LayoutParams(width, height, gravity)
    layoutParams.setMargins(margins.left, margins.top, margins.right, margins.bottom)
    return layoutParams
}


fun linear(
    width: Int,
    height: Int,
    gravity: Int = Gravity.NO_GRAVITY,
    weight: Float = 0f,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0
): LinearLayout.LayoutParams {
    val margins = Rect(marginLeft, marginTop, marginRight, marginBottom)
    return linear(width, height, gravity, weight, margins)
}

fun linear(
    width: Int,
    height: Int,
    gravity: Int = Gravity.NO_GRAVITY,
    weight: Float = 0f,
    margins: Rect = Rect()
): LinearLayout.LayoutParams {
    val layoutParams = LinearLayout.LayoutParams(width, height, weight)
    layoutParams.setMargins(margins.left, margins.top, margins.right, margins.bottom)
    layoutParams.gravity = gravity
    return layoutParams
}

fun Paint.measureText(text: CharSequence): Float {
    return measureText(text, 0, text.length)
}