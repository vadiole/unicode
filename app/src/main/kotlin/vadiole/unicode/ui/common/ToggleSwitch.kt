package vadiole.unicode.ui.common

import android.animation.ArgbEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Switch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import vadiole.unicode.R

class ToggleSwitch(context: Context) : Switch(context) {

    var onToggle: ((Boolean) -> Unit)? = null

    private val evaluator = ArgbEvaluator()
    private val offTrack = context.getColor(R.color.windowDivider)
    private val onTrack = context.getColor(R.color.windowTextPrimary)
    private val offThumb = context.getColor(R.color.windowTextSecondary)
    private val onThumb = context.getColor(R.color.layer0)

    private val track = TrackDrawable()
    private var thumb: ThumbDrawable? = null

    private var progress = 0f
    private var colorAnimator: ValueAnimator? = null
    private var syncing = false

    init {
        background = null
        showText = false
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        switchMinWidth = 52.dp(context)
        trackDrawable = track
        thumb = ThumbDrawable().also { thumbDrawable = it }
        applyProgress(0f)
    }

    fun setState(checked: Boolean, animate: Boolean) {
        if (isChecked == checked) return
        settle(checked, animate)
    }

    override fun setChecked(checked: Boolean) {
        if (syncing || thumb == null) {
            super.setChecked(checked)
            return
        }
        val current = isChecked
        settle(checked, animate = true)
        if (checked != current) onToggle?.invoke(checked)
    }

    private fun settle(checked: Boolean, animate: Boolean) {
        val captured = thumb?.capture() ?: false
        syncing = true
        super.setChecked(checked)
        syncing = false
        jumpDrawablesToCurrentState()
        val smooth = animate && isAttachedToWindow && isLaidOut && animatorsEnabled
        if (smooth && captured) thumb?.spring(checked) else thumb?.snap(checked)
        moveColors(if (checked) 1f else 0f, smooth)
    }

    private fun moveColors(target: Float, animate: Boolean) {
        colorAnimator?.cancel()
        if (progress == target) return
        if (!animate) {
            applyProgress(target)
            return
        }
        colorAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 250L
            interpolator = ACCELERATE_DECELERATE
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        track.setColor(evaluator.evaluate(value, offTrack, onTrack) as Int)
        thumb?.setColor(evaluator.evaluate(value, offThumb, onThumb) as Int)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        colorAnimator?.cancel()
        thumb?.snap(isChecked)
        applyProgress(if (isChecked) 1f else 0f)
    }

    private inner class TrackDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val radius = 16.dp(context).toFloat()
        private val rect = RectF()
        private val path = Path()

        fun setColor(color: Int) {
            paint.color = color
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            rect.set(bounds)
            path.rewind()
            path.addSquircle(rect, radius)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun getIntrinsicWidth(): Int = 52.dp(context)

        override fun getIntrinsicHeight(): Int = 32.dp(context)

        override fun getPadding(padding: Rect): Boolean {
            padding.set(8.dp(context), 0, 8.dp(context), 0)
            return true
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private inner class ThumbDrawable : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val intrinsic = 28.dp(context)
        private val sidePadding = 6.dp(context)
        private val diameterOff = 16f.dp(context)
        private val diameterOn = 24f.dp(context)
        private val diameterPressed = 28f.dp(context)

        private var diameter = diameterOff
        private var slide = 0f
        private var restingChecked = false
        private var pressed = false
        private var pendingFromCx = Float.NaN
        private var capturedCx = Float.NaN
        private var capturedDiameter = 0f
        private var sizeAnimator: ValueAnimator? = null
        private var slideAnimator: ValueAnimator? = null

        fun setColor(color: Int) {
            paint.color = color
            invalidateSelf()
        }

        fun snap(checked: Boolean) {
            restingChecked = checked
            sizeAnimator?.cancel()
            slideAnimator?.cancel()
            pendingFromCx = Float.NaN
            diameter = restingDiameter()
            slide = 0f
            invalidateSelf()
        }

        fun capture(): Boolean {
            if (bounds.isEmpty) return false
            capturedCx = if (pendingFromCx.isNaN()) bounds.exactCenterX() + slide else pendingFromCx
            capturedDiameter = diameter
            return true
        }

        fun spring(checked: Boolean) {
            restingChecked = checked
            pendingFromCx = capturedCx
            diameter = capturedDiameter
            animateSize(restingDiameter())
            invalidateSelf()
        }

        private fun restingDiameter(): Float = when {
            pressed -> diameterPressed
            restingChecked -> diameterOn
            else -> diameterOff
        }

        private fun animateSize(target: Float) {
            sizeAnimator?.cancel()
            if (diameter == target) return
            sizeAnimator = ValueAnimator.ofFloat(diameter, target).apply {
                duration = SPRING_DURATION
                interpolator = SPRING
                addUpdateListener { diameter = it.animatedValue as Float; invalidateSelf() }
                start()
            }
        }

        private fun startSlide(from: Float) {
            slideAnimator?.cancel()
            slide = from
            if (from == 0f) return
            slideAnimator = ValueAnimator.ofFloat(from, 0f).apply {
                duration = SPRING_DURATION
                interpolator = SPRING
                addUpdateListener { slide = it.animatedValue as Float; invalidateSelf() }
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            if (!pendingFromCx.isNaN()) {
                val from = pendingFromCx - bounds.exactCenterX()
                pendingFromCx = Float.NaN
                startSlide(from)
            }
            canvas.drawCircle(bounds.exactCenterX() + slide, bounds.exactCenterY(), diameter / 2f, paint)
        }

        override fun isStateful(): Boolean = true

        override fun onStateChange(state: IntArray): Boolean {
            val isPressed = state.contains(android.R.attr.state_pressed)
            if (pressed == isPressed) return false
            pressed = isPressed
            if (pressed) {
                sizeAnimator?.cancel()
                diameter = diameterPressed
                invalidateSelf()
            } else {
                animateSize(restingDiameter())
            }
            return true
        }

        override fun jumpToCurrentState() {
            snap(restingChecked)
        }

        override fun getIntrinsicWidth(): Int = intrinsic

        override fun getIntrinsicHeight(): Int = intrinsic

        override fun getPadding(padding: Rect): Boolean {
            padding.set(sidePadding, 0, sidePadding, 0)
            return true
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val SPRING_DURATION = 350L

        // M3 FastSpatial spring constants:
        // https://github.com/androidx/androidx/blob/a0c93ebc1c59237652ad316bf1a1b177e0059e6f/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt#L26-L27
        const val DECAY = 16.970562f
        const val OMEGA = 22.627417f
        val SPRING = TimeInterpolator { fraction ->
            val t = fraction * (SPRING_DURATION / 1000f)
            1f - exp(-DECAY * t) * (cos(OMEGA * t) + DECAY / OMEGA * sin(OMEGA * t))
        }
        val ACCELERATE_DECELERATE = AccelerateDecelerateInterpolator()
    }
}
