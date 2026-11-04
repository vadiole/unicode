package vadiole.unicode.ui.settings

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
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import vadiole.unicode.R
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.animatorsEnabled
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.mono_regular

class HoldActionRow(context: Context, label: String, private val action: () -> Unit) : TextView(context) {
    private val progressPaint = Paint().apply { color = context.getColor(R.color.layer1Pressed) }
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
    private var releaseAnimation: AnimatorSet? = null
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
        minimumHeight = 56.dp(context)
        setPadding(16.dp(context), 12.dp(context), 16.dp(context), 12.dp(context))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        typeface = mono_regular
        includeFontPadding = false
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        setTextColor(context.getColor(R.color.windowTextPrimary))
        text = label
        isClickable = true
        isFocusable = true
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
            MotionEvent.ACTION_CANCEL -> releaseHold(nudge = false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (progress > 0f) canvas.drawRect(0f, 0f, width * progress, height.toFloat(), progressPaint)
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
        releaseHold(nudge = false)
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
        progress = 0f
        invalidate()
    }

    private companion object {
        const val HOLD_DURATION = 1000L
        const val TICK_INTERVAL = 100L
    }
}
