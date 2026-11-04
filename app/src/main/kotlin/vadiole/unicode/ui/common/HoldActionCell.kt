package vadiole.unicode.ui.common

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import vadiole.unicode.R

class HoldActionCell(context: Context, label: String, private val action: () -> Unit) : ActionCell(context, label) {
    private val progressPaint = Paint().apply {
        val ripple = context.getColor(R.color.ripple)
        color = ripple and 0xFFFFFF or (minOf(255, (ripple ushr 24) * 2) shl 24)
    }
    private val progressAlpha = progressPaint.alpha
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val vibrator = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else null
    private val tickEffect = if (Build.VERSION.SDK_INT >= 33 &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
    ) {
        VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f)
            .compose()
    } else null
    private val touchAttributes = if (Build.VERSION.SDK_INT >= 33) {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    } else null
    private var downX = 0f
    private var downY = 0f
    private var startedAt = 0L
    private var holding = false
    private var progress = 0f
    private var releaseAnimation: Animator? = null
    private val complete = Runnable { finishHold() }
    private val tick = object : Runnable {
        override fun run() {
            if (!holding) return
            if (Build.VERSION.SDK_INT >= 33 && tickEffect != null) {
                vibrator?.vibrate(tickEffect, touchAttributes!!)
            } else {
                val constant = if (Build.VERSION.SDK_INT >= 34) {
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
                performHapticFeedback(constant)
            }
            postDelayed(this, TICK_INTERVAL)
        }
    }
    private val frame = object : Runnable {
        override fun run() {
            if (!holding) return
            progress = ((SystemClock.uptimeMillis() - startedAt) / HOLD_DURATION.toFloat()).coerceAtMost(1f)
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        setOnClickListener { releaseHold(nudge = true) }
        setOnLongClickListener {
            if (isEnabled) action()
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetHold()
                downX = event.x
                downY = event.y
                startedAt = SystemClock.uptimeMillis()
                holding = true
                isPressed = true
                postDelayed(complete, HOLD_DURATION)
                postDelayed(tick, TICK_INTERVAL)
                postOnAnimation(frame)
            }
            MotionEvent.ACTION_MOVE -> if (holding &&
                (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop)
            ) releaseHold(nudge = false)
            MotionEvent.ACTION_UP -> if (holding) {
                if (SystemClock.uptimeMillis() - startedAt >= HOLD_DURATION) finishHold()
                else performClick()
            }
            MotionEvent.ACTION_CANCEL -> if (holding) releaseHold(nudge = false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (progress > 0f) {
            val count = canvas.save()
            canvas.clipPath(shapePath)
            canvas.drawRect(0f, 0f, width * progress, height.toFloat(), progressPaint)
            canvas.restoreToCount(count)
        }
        super.onDraw(canvas)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.4f
        if (!enabled) resetHold()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetHold()
    }

    override fun onDetachedFromWindow() {
        resetHold()
        super.onDetachedFromWindow()
    }

    private fun finishHold() {
        if (!holding || !isEnabled) return
        stopHold()
        progress = 1f
        invalidate()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        action()
        if (!animatorsEnabled) {
            resetHold()
            return
        }
        releaseAnimation = ValueAnimator.ofInt(progressAlpha, 0).apply {
            startDelay = 120L
            duration = 180L
            interpolator = LinearInterpolator()
            addUpdateListener {
                progressPaint.alpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun releaseHold(nudge: Boolean) {
        stopHold()
        releaseAnimation?.cancel()
        if (!animatorsEnabled) {
            progress = 0f
            invalidate()
            return
        }
        val start = progress
        val peak = if (nudge) maxOf(start, 0.15f) else start
        val retreat = progressAnimator(peak, 0f, 180L)
        releaseAnimation = AnimatorSet().apply {
            if (start < peak) playSequentially(progressAnimator(start, peak, 60L), retreat)
            else play(retreat)
            start()
        }
    }

    private fun progressAnimator(from: Float, to: Float, durationMs: Long) = ValueAnimator.ofFloat(from, to).apply {
        duration = durationMs
        interpolator = DecelerateInterpolator(1.8f)
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private fun stopHold() {
        holding = false
        isPressed = false
        removeCallbacks(complete)
        removeCallbacks(tick)
        removeCallbacks(frame)
    }

    private fun resetHold() {
        stopHold()
        releaseAnimation?.cancel()
        releaseAnimation = null
        progressPaint.alpha = progressAlpha
        progress = 0f
        invalidate()
    }

    private companion object {
        const val HOLD_DURATION = 1000L
        const val TICK_INTERVAL = 100L
    }
}
