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

class AppTheme(private var colors: HashMap<String, Int>) {
    init {
        sharedDividerPaint.color = getColor(key_windowDivider)
    }

    fun applyColors(value: HashMap<String, Int>) {
        colors = value
        sharedDividerPaint.color = getColor(key_windowDivider)
    }

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

    companion object {
        val blueDark = hashMapOf(
            key_dialogBackground to 0xFF1C1D1E.toInt(),
            key_dialogSurface to 0xFF_2D2E2F.toInt(),
            key_dialogSurfacePressed to 0xFF_3E3E3F.toInt(),
            key_dialogIcon to 0xFF_FFFFFF.toInt(),
            key_dialogButton to 0xFF_64B5EF.toInt(),
            key_dialogButtonDanger to 0xFF_D9635F.toInt(),
            key_dialogListRipple to 0x14_E5F2FF,
            key_dialogDim to 0xFF_000000.toInt(),
            key_windowBackground to 0xFF_1C1D1E.toInt(),
            key_windowTextPrimary to 0xFF_FFFFFF.toInt(),
            key_windowTextSecondary to 0xFF_989899.toInt(),
            key_windowTextAccent to 0xFF_55A4F8.toInt(),
            key_windowDivider to 0x53_545558,
            key_windowRipple to 0x14_E5F2FF,
            key_topBarBackground to 0xFF_1E1E1F.toInt(),
            key_tabBarBackground to 0xFF_1E1E1F.toInt(),
            key_tabBarItem to 0xFF_B5C2D3.toInt(),
            key_tabBarItemSelected to 0xFF_55A4F8.toInt(),
        )

        val blueLight = hashMapOf(
            key_dialogBackground to 0xFF_F8F8F9.toInt(),
            key_dialogSurface to 0xFF_FFFFFF.toInt(),
            key_dialogSurfacePressed to 0xFF_E5E5E7.toInt(),
            key_dialogIcon to 0xFF_000000.toInt(),
            key_dialogButton to 0xFF_3E76C6.toInt(),
            key_dialogButtonDanger to 0xFF_EC5242.toInt(),
            key_dialogListRipple to 0x0F_000000,
            key_dialogDim to 0xFF_000000.toInt(),
            key_windowBackground to 0xFF_FFFFFF.toInt(),
            key_windowTextPrimary to 0xFF_000000.toInt(),
            key_windowTextSecondary to 0xFF_88888C.toInt(),
            key_windowTextAccent to 0xFF_417FC6.toInt(),
            key_windowDivider to 0x2E_3C3C43,
            key_windowRipple to 0x0F_000000,
            key_topBarBackground to 0xFF_FFFFFF.toInt(),
            key_tabBarBackground to 0xFF_FFFFFF.toInt(),
            key_tabBarItem to 0xFF_9F9E9E.toInt(),
            key_tabBarItemSelected to 0xFF_417FC6.toInt(),
        )
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