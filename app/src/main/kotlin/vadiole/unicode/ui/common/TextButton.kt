package vadiole.unicode.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import vadiole.unicode.ui.theme.roboto_regular
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.setPaddingHorizontal

open class TextButton(context: Context) : SimpleTextView(context) {
    var colors: ColorStateList = ColorStateList.valueOf(Color.DKGRAY)
        set(value) {
            field = value
            stateAnimator.cancel()
            textColor = value.getColorForState(drawableState, value.defaultColor)
        }
    private var stateAnimator: ValueAnimator = ValueAnimator.ofArgb().apply {
        duration = 120L
        addUpdateListener { animator ->
            textColor = animator.animatedValue as Int
            postInvalidateOnAnimation()
        }
    }

    init {
        setPaddingHorizontal(8.dp(context))
        isClickable = true
        isFocusable = true
        typeface = roboto_regular
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        val newColor = colors.getColorForState(drawableState, textColor)
        if (stateAnimator.isRunning || newColor != textColor) {
            stateAnimator.cancel()
            stateAnimator.setIntValues(textColor, newColor)
            stateAnimator.setEvaluator(StateColorDrawable.evaluator)
            stateAnimator.start()
        }
    }
}