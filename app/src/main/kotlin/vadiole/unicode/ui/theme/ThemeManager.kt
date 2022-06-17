package vadiole.unicode.ui.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape
import android.util.StateSet
import android.view.View

class ThemeManager(var colors: HashMap<String, Int>) {

    private val observers = mutableSetOf<ThemeDelegate>()
    val dividerPaint = Paint().apply {
        isAntiAlias = false
        strokeWidth = 1f
    }

    init {
        dividerPaint.color = getColor(key_windowDivider)
    }

    fun observe(observer: ThemeDelegate) {
        if (observer.isAttachedToWindow()) {
            observer.applyTheme()
        }
        observer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observers.add(observer)
                observer.applyTheme()
            }

            override fun onViewDetachedFromWindow(v: View) {
                observers.remove(observer)
            }
        })
    }

    fun setThemeColors(value: HashMap<String, Int>) {
        colors = value
        dividerPaint.color = getColor(key_windowDivider)
        observers.forEach { observer ->
            observer.applyTheme()
        }
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

    // I'm thinking to refactor this part🤔
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