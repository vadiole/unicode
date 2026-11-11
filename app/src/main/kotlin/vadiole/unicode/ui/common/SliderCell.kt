package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt
import vadiole.unicode.R

class SliderCell(
    context: Context,
    name: String,
    range: ClosedFloatingPointRange<Float>,
    initialValue: Float,
    private val formatValue: (Float) -> String = { "${(it * 100).roundToInt()}%" },
) : ViewGroup(context) {
    val slider = Slider(context, range)

    var touchBelow = 0
        private set

    private val label = ActionCell(context, name).apply {
        background = null
        isClickable = false
        isFocusable = false
        valueMaxScale = LABEL_PRESSED_SCALE
        minHeight = (48 - LABEL_TRIM).dp(context)
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom - LABEL_TRIM.dp(context))
    }
    private val sliderInset = 16.dp(context)
    private val sliderHeight = SLIDER_HEIGHT.dp(context)
    private val sliderBottom = SLIDER_BOTTOM.dp(context)
    private var card: SquircleDrawable? = null
    private val labelColor = context.getColor(R.color.windowTextSecondary)
    private val labelPressedColor = context.getColor(R.color.primaryControl)
    private val labelSpring = SpringAnimator(LABEL_STIFFNESS, minVisibleChange = 0.002f, onUpdate = ::applyLabelProgress)

    init {
        setCorners(top = true, bottom = true)
        addView(label)
        addView(slider)
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
        card = SquircleDrawable(0, t, t, b, b).apply {
            colors = ColorStateList.valueOf(context.getColor(R.color.layer1))
        }
        updateBackground()
    }

    fun extendTouchBelow(extra: Int) {
        if (touchBelow == extra) return
        touchBelow = extra
        updateBackground()
        requestLayout()
    }

    private fun updateBackground() {
        background = if (touchBelow == 0) card else InsetDrawable(card, 0, 0, 0, touchBelow)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val exactWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        label.measure(exactWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val top = label.measuredHeight
        val bottom = sliderBottom + touchBelow
        if (slider.paddingTop != top || slider.paddingBottom != bottom) slider.setPadding(sliderInset, top, sliderInset, bottom)
        val height = top + sliderHeight + bottom
        slider.measure(exactWidth, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        label.layout(0, 0, label.measuredWidth, label.measuredHeight)
        slider.layout(0, 0, slider.measuredWidth, slider.measuredHeight)
    }

    fun setValue(value: Float) {
        label.value = formatValue(value)
    }

    private fun animateLabel(pressed: Boolean) {
        val target = if (pressed) 1f else 0f
        if (!animatorsEnabled) {
            labelSpring.cancel()
            labelSpring.value = target
            applyLabelProgress(target)
            return
        }
        labelSpring.animateTo(target)
    }

    private fun applyLabelProgress(progress: Float) {
        label.valueScale = 1f + (LABEL_PRESSED_SCALE - 1f) * progress
        label.setValueColor(ColorUtils.blendARGB(labelColor, labelPressedColor, progress.coerceIn(0f, 1f)))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        labelSpring.cancel()
        labelSpring.value = 0f
        applyLabelProgress(0f)
    }

    private companion object {
        const val LABEL_PRESSED_SCALE = 1.15f
        const val LABEL_TRIM = 8
        const val SLIDER_HEIGHT = 32
        const val SLIDER_BOTTOM = 8
        const val LABEL_STIFFNESS = 500f
    }
}
