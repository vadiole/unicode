package vadiole.unicode.ui.theme

import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.util.StateSet
import android.view.View

abstract class Theme {
    protected abstract var colors: HashMap<String, Int>

    fun getColor(key: String): Int {
        return colors[key] ?: Color.RED
    }

    fun getColors(key: String): ColorStateList {
        val color = getColor(key)
        return ColorStateList.valueOf(color)
    }

    fun getColors(states: Array<IntArray>, keys: Array<String>): ColorStateList {
        val colors = IntArray(keys.size) {
            getColor(keys[it])
        }
        return ColorStateList(states, colors)
    }

    fun getColorDrawable(key: String): ColorDrawable {
        val color = getColor(key)
        return ColorDrawable(color)
    }

    fun getTint(key: String): PorterDuffColorFilter {
        val color = getColor(key)
        return PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
    }

    fun getRippleRect(key: String, radius: Float = 0f, content: Drawable? = null): RippleDrawable {
        val colorStateList = ColorStateList.valueOf(getColor(key))
        val shape = if (radius == 0f) {
            RectShape()
        } else {
            val corners = FloatArray(8) { radius }
            RoundRectShape(corners, null, null)
        }
        val mask = ShapeDrawable(shape)
        return RippleDrawable(colorStateList, content, mask)
    }

    fun getRippleCircle(key: String, diameter: Float = -1f, content: Drawable? = null): RippleDrawable {
        val rippleColor = getColor(key)
        val colorStateList = ColorStateList(
            arrayOf(StateSet.WILD_CARD),
            intArrayOf(rippleColor)
        )
        val mask = if (diameter >= 0) {
            ShapeDrawable(OvalShape().also {
                it.resize(diameter, diameter)
            })
        } else {
            null
        }
        return RippleDrawable(colorStateList, content, mask)
    }
}

val sharedDividerPaint = Paint().apply {
    flags = flags and Paint.ANTI_ALIAS_FLAG.inv()
    strokeWidth = 1f
}

private const val semiboldPath = "font/roboto_semibold.otf"
val View.roboto_semibold: Typeface
    get() = Typeface.createFromAsset(resources.assets, semiboldPath)

private const val regularPath = "font/roboto_regular.otf"
val View.roboto_regular: Typeface
    get() = Typeface.createFromAsset(resources.assets, regularPath)


const val key_dialogBackground = "dialogBackground"
const val key_dialogSurface = "dialogSurface"
const val key_dialogSurfacePressed = "dialogSurfacePressed"
const val key_dialogIcon = "dialogIcon"
const val key_dialogButton = "dialogButton"
const val key_dialogButtonDanger = "dialogButtonDanger"
const val key_dialogListRipple = "dialogListRipple"
const val key_dialogDim = "dialogDim"
const val key_windowBackground = "windowBackground"
const val key_windowTextPrimary = "windowTextPrimary"
const val key_windowTextSecondary = "windowTextSecondary"
const val key_windowTextAccent = "windowTextAccent"
const val key_windowDivider = "windowDivider"
const val key_windowRipple = "windowRipple"
const val key_tabBarBackground = "key_tabBarBackground"
const val key_tabBarItem = "key_tabBarItem"
const val key_tabBarItemSelected = "key_tabBarItemSelected"
const val key_topBarBackground = "key_toolBarBackground"