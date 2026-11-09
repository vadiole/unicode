package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.widget.LinearLayout
import kotlin.math.roundToInt
import vadiole.unicode.R

class SliderCell(
    context: Context,
    name: String,
    range: ClosedFloatingPointRange<Float>,
    initialValue: Float,
    private val formatValue: (Float) -> String = { "${(it * 100).roundToInt()}%" },
) : LinearLayout(context) {
    val slider = Slider(context, range)
    private val label = ActionCell(context, name).apply {
        background = null
        isClickable = false
        isFocusable = false
    }

    init {
        orientation = VERTICAL
        setCorners(top = true, bottom = true)
        addView(label, linearParams(matchParent, wrapContent))
        addView(slider, linearParams(matchParent, 48.dp(context), marginLeft = 16.dp(context), marginRight = 16.dp(context), marginBottom = 8.dp(context)))
        slider.contentDescription = name
        slider.value = initialValue
        setValue(initialValue)
    }

    fun setCorners(top: Boolean, bottom: Boolean) {
        val radius = 13.dp(context)
        val t = if (top) radius else 0
        val b = if (bottom) radius else 0
        background = SquircleDrawable(0, t, t, b, b).apply {
            colors = ColorStateList.valueOf(context.getColor(R.color.layer1))
        }
    }

    fun setValue(value: Float) {
        label.value = formatValue(value)
    }
}
