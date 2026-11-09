package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
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
    private val labelColor = context.getColor(R.color.windowTextSecondary)
    private val labelPressedColor = context.getColor(R.color.primaryControl)
    private val labelProgress = FloatValueHolder(0f)
    private val labelSpring = SpringAnimation(labelProgress).apply {
        setMinimumVisibleChange(0.002f)
        spring = SpringForce(0f).setStiffness(LABEL_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        addUpdateListener { _, progress, _ -> applyLabelProgress(progress) }
    }

    init {
        orientation = VERTICAL
        setCorners(top = true, bottom = true)
        addView(label, linearParams(matchParent, wrapContent))
        val padding = 16.dp(context)
        slider.setPadding(padding, 0, padding, 0)
        addView(slider, linearParams(matchParent, 48.dp(context), marginBottom = 8.dp(context)))
        slider.contentDescription = name
        slider.formatValue = formatValue
        slider.onPressedChanged = ::animateLabel
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

    private fun animateLabel(pressed: Boolean) {
        val target = if (pressed) 1f else 0f
        if (!animatorsEnabled) {
            labelSpring.cancel()
            labelProgress.value = target
            applyLabelProgress(target)
            return
        }
        labelSpring.animateToFinalPosition(target)
    }

    private fun applyLabelProgress(progress: Float) {
        label.valueScale = 1f + (LABEL_PRESSED_SCALE - 1f) * progress
        label.valueColor = ColorUtils.blendARGB(labelColor, labelPressedColor, progress.coerceIn(0f, 1f))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        labelSpring.cancel()
        labelProgress.value = 0f
        applyLabelProgress(0f)
    }

    private companion object {
        const val LABEL_PRESSED_SCALE = 1.15f
        const val LABEL_STIFFNESS = 500f
    }
}
