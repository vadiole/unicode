package vadiole.unicode.ui.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

open class StateColorDrawable : Drawable() {
    var colors: ColorStateList = ColorStateList.valueOf(Color.DKGRAY)
        set(value) {
            field = value
            stateAnimator.cancel()
            paint.color = value.getColorForState(state, value.defaultColor)
            invalidateSelf()
        }
    private var stateAnimator: ValueAnimator = ValueAnimator.ofArgb().apply {
        duration = 120L
        addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            paint.color = color
            invalidateSelf()
        }
    }
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun isStateful(): Boolean = true

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onStateChange(state: IntArray): Boolean {
        val newColor = colors.getColorForState(state, paint.color)
        return if (stateAnimator.isRunning || newColor != paint.color) {
            stateAnimator.cancel()
            stateAnimator.setIntValues(paint.color, newColor)
            stateAnimator.setEvaluator(evaluator)
            stateAnimator.start()
            invalidateSelf()
            true
        } else {
            false
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(canvas.clipBounds, paint)
    }

    companion object {
        val evaluator = ArgbEvaluator()
    }
}