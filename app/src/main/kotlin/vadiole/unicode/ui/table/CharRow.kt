package vadiole.unicode.ui.table

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.floor
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.ui.common.dp

class CharRow(
    context: Context,
    private val count: Int,
    private val delegate: Delegate,
) : View(context) {
    private var codePoints: CodePointArray = CodePointArray(0)
    private var abbreviations: Map<CodePoint, String> = emptyMap()
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val abbreviationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val longClickRunnable = object : Runnable {
        override fun run() {
            val index = actionDownIndex
            if (index >= 0) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val codePoint = codePoints[index]
                delegate.onLongClick(codePoint)
                releaseClick(index)
            }
        }
    }
    private val clearHighlightRunnable = object : Runnable {
        override fun run() = clearHighlight()
    }
    private val longClickDuration: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val charSize = 144f.dp(context) / count
    private val abbreviationSize = charSize * 0.5f
    private val charRipples: BooleanArray = BooleanArray(count)
    private val charCoordsX: FloatArray = FloatArray(count)
    private var charCoordY: Float = 0f
    private var abbreviationCoordY: Float = 0f
    private var charPivotY: Float = 0f
    private var rippleRadius: Float = 200f.dp(context) / count
    private var actionDownIndex = -1
    private var highlightIndex = -1
    private val rippleAlpha: Int
    private var rippleIndex = -1
    private var rippleTouchX = 0f
    private var rippleTouchY = 0f
    private var rippleGrow = 0f
    private var rippleFade = 1f
    private var isRippleReleased = false
    private val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val rippleGrowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = easeOut
        addUpdateListener {
            rippleGrow = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationStart(animation: Animator) {
                cancelled = false
            }

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && isRippleReleased) fadeRipple()
            }
        })
    }
    private val rippleFadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = RIPPLE_FADE_DURATION
        interpolator = easeOut
        addUpdateListener {
            rippleFade = it.animatedValue as Float
            if (rippleFade <= 0f) rippleIndex = -1
            invalidate()
        }
    }

    init {
        charPaint.color = context.getColor(R.color.windowTextPrimary)
        abbreviationPaint.color = ColorUtils.setAlphaComponent(context.getColor(R.color.windowTextPrimary), 128)
        ripplePaint.color = context.getColor(R.color.ripple)
        rippleAlpha = ripplePaint.alpha
        charPaint.textSize = charSize
        abbreviationPaint.textSize = abbreviationSize
        isClickable = true
        isFocusable = true
    }

    fun bind(codePoints: CodePointArray, abbreviations: Map<CodePoint, String>) {
        this.codePoints = codePoints
        this.abbreviations = abbreviations
        charRipples.fill(false)
        clearRipple()
        invalidate()
    }

    fun highlightChar(index: Int) {
        clearHighlight()
        charRipples[index] = true
        highlightIndex = index
        handler.postDelayed(clearHighlightRunnable, 2000)
    }

    private fun clearHighlight() {
        if (highlightIndex != -1) {
            charRipples[highlightIndex] = false
            invalidate()
            highlightIndex = -1
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = floor(event.x / width * count).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (index >= codePoints.size) return false
                actionDownIndex = index
                startRipple(index, event.x, event.y)
                postDelayed(longClickRunnable, longClickDuration)
            }

            MotionEvent.ACTION_UP -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex == index) {
                        val codePoint = codePoints[index]
                        delegate.onClick(codePoint, this, charCoordsX[index], charPivotY, charSize)
                    }
                    releaseClick(actionDownIndex)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (actionDownIndex >= 0) {
                    cancelClick(actionDownIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (actionDownIndex >= 0 && actionDownIndex != index) {
                    cancelClick(actionDownIndex)
                    return false
                }
            }
        }
        return true
    }

    private fun releaseClick(index: Int) {
        if (actionDownIndex != index) return
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        isRippleReleased = true
        if (rippleGrowAnimator.isRunning) {
            val from = rippleGrow
            rippleGrowAnimator.cancel()
            rippleGrowAnimator.setFloatValues(from, 1f)
            rippleGrowAnimator.duration = RIPPLE_FINISH_DURATION
            rippleGrowAnimator.start()
        } else {
            fadeRipple()
        }
    }

    private fun cancelClick(index: Int) {
        if (actionDownIndex != index) return
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        isRippleReleased = true
        rippleGrowAnimator.cancel()
        fadeRipple()
    }

    private fun startRipple(index: Int, x: Float, y: Float) {
        rippleGrowAnimator.cancel()
        rippleFadeAnimator.cancel()
        rippleIndex = index
        rippleTouchX = x
        rippleTouchY = y
        rippleFade = 1f
        isRippleReleased = false
        if (ValueAnimator.areAnimatorsEnabled()) {
            rippleGrow = 0f
            rippleGrowAnimator.setFloatValues(0f, 1f)
            rippleGrowAnimator.duration = RIPPLE_GROW_DURATION
            rippleGrowAnimator.start()
        } else {
            rippleGrow = 1f
        }
        invalidate()
    }

    private fun fadeRipple() {
        if (rippleIndex < 0) return
        rippleFadeAnimator.cancel()
        if (ValueAnimator.areAnimatorsEnabled()) {
            rippleFadeAnimator.setFloatValues(rippleFade, 0f)
            rippleFadeAnimator.start()
        } else {
            clearRipple()
        }
    }

    private fun clearRipple() {
        rippleGrowAnimator.cancel()
        rippleFadeAnimator.cancel()
        rippleIndex = -1
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearRipple()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val singleCharWidth = w / count
        val centerOffsetX = singleCharWidth / 2f
        repeat(count) { index ->
            charCoordsX[index] = singleCharWidth * index + centerOffsetX
        }
        charCoordY = (h - charPaint.descent() - charPaint.ascent()) / 2
        abbreviationCoordY = (h - abbreviationPaint.descent() - abbreviationPaint.ascent()) / 2
        charPivotY = h / 2f
    }

    override fun onMeasure(width: Int, height: Int) {
        val widthSize = MeasureSpec.getSize(width)
        val heightSpec = MeasureSpec.makeMeasureSpec(widthSize / count, MeasureSpec.EXACTLY)
        super.onMeasure(width, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        codePoints.forEachIndexed { index, codePoint ->
            val charCoordX = charCoordsX[index]
            val charPivotX = charCoordsX[index]
            if (charRipples[index]) {
                canvas.drawCircle(charPivotX, charPivotY, rippleRadius, ripplePaint)
            } else if (index == rippleIndex) {
                drawRipple(canvas, charPivotX)
            }
            drawChar(canvas, codePoint, charCoordX, charCoordY)
        }
    }

    private fun drawRipple(canvas: Canvas, centerX: Float) {
        val g = rippleGrow
        val x = rippleTouchX + (centerX - rippleTouchX) * g
        val y = rippleTouchY + (charPivotY - rippleTouchY) * g
        val radius = rippleRadius * (RIPPLE_START_RADIUS + (1f - RIPPLE_START_RADIUS) * g)
        ripplePaint.alpha = (rippleAlpha * rippleFade).toInt()
        canvas.drawCircle(x, y, radius, ripplePaint)
        ripplePaint.alpha = rippleAlpha
    }

    private fun drawChar(canvas: Canvas, codePoint: CodePoint, charCoordX: Float, charCoordY: Float) {
        val abbreviation = abbreviations[codePoint]
        if (abbreviation != null) {
            canvas.drawText(abbreviation, charCoordX, abbreviationCoordY, abbreviationPaint)
        } else {
            canvas.drawText(codePoint.char, charCoordX, charCoordY, charPaint)
        }
    }

    override fun hasOverlappingRendering() = false

    interface Delegate {
        fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float)
        fun onLongClick(codePoint: CodePoint)
    }

    private companion object {
        const val RIPPLE_GROW_DURATION = 400L
        const val RIPPLE_FINISH_DURATION = 150L
        const val RIPPLE_FADE_DURATION = 200L
        const val RIPPLE_START_RADIUS = 0.2f
    }
}