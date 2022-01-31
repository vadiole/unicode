package vadiole.unicode.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.util.Property
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import kotlin.math.ceil

const val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
const val wrapContent = ViewGroup.LayoutParams.WRAP_CONTENT

fun Float.dp(context: Context) = this * context.resources.displayMetrics.density

fun Int.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()

val WindowInsetsCompat.navigationBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.navigationBars())

val WindowInsetsCompat.statusBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.statusBars())



fun frameParams(
    width: Int,
    height: Int,
    gravity: Int = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY,
    marginLeft: Int = 0,
    marginTop: Int = 0,
    marginRight: Int = 0,
    marginBottom: Int = 0
): FrameLayout.LayoutParams {
    val margins = Rect(marginLeft, marginTop, marginRight, marginBottom)
    return frameParams(width, height, gravity, margins)
}

fun frameParams(
    width: Int,
    height: Int,
    gravity: Int = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY,
    margins: Rect = Rect()
): FrameLayout.LayoutParams {
    val layoutParams = FrameLayout.LayoutParams(width, height, gravity)
    layoutParams.gravity
    layoutParams.setMargins(margins.left, margins.top, margins.right, margins.bottom)
    return layoutParams
}

fun linearParams(
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
    return linearParams(width, height, gravity, weight, margins)
}

fun linearParams(
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

fun <T : View> T.animate(
    property: Property<T, Float>,
    vararg values: Float, apply: ObjectAnimator.() -> Unit = {}
): ObjectAnimator = ObjectAnimator.ofFloat(this, property, *values).apply(apply)
