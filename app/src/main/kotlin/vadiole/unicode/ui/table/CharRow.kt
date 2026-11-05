package vadiole.unicode.ui.table

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.floor
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.rippleDrawable

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
    private val maxRippleRadius = 200f.dp(context) / count
    private val rippleInset = 1.5f.dp(context)
    private var rippleRadius: Float = maxRippleRadius
    private var actionDownIndex = -1
    private var highlightIndex = -1
    private val ripple = context.rippleDrawable(mask = ShapeDrawable(OvalShape())).apply {
        callback = this@CharRow
    }

    init {
        charPaint.color = context.getColor(R.color.windowTextPrimary)
        abbreviationPaint.color = context.getColor(R.color.characterAbbreviation)
        ripplePaint.color = context.getColor(R.color.ripple)
        charPaint.textSize = charSize
        abbreviationPaint.textSize = abbreviationSize
        isClickable = true
        isFocusable = true
    }

    fun bind(codePoints: CodePointArray, abbreviations: Map<CodePoint, String>) {
        this.codePoints = codePoints
        this.abbreviations = abbreviations
        removeCallbacks(clearHighlightRunnable)
        clearHighlight()
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
                if (index !in codePoints.indices) return false
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
                    releaseClick(actionDownIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex != index) {
                        releaseClick(actionDownIndex)
                        return false
                    }
                    ripple.setHotspot(event.x, event.y)
                }
            }
        }
        return true
    }

    private fun releaseClick(index: Int) {
        if (actionDownIndex != index) return
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        ripple.state = EMPTY_STATE
    }

    private fun startRipple(index: Int, x: Float, y: Float) {
        clearRipple()
        actionDownIndex = index
        val centerX = charCoordsX[index]
        ripple.setBounds(
            (centerX - rippleRadius).toInt(),
            (charPivotY - rippleRadius).toInt(),
            (centerX + rippleRadius).toInt(),
            (charPivotY + rippleRadius).toInt(),
        )
        ripple.setHotspot(x, y)
        ripple.state = PRESSED_STATE
    }

    private fun clearRipple() {
        ripple.state = EMPTY_STATE
        ripple.jumpToCurrentState()
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who === ripple || super.verifyDrawable(who)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        ripple.jumpToCurrentState()
    }

    override fun onDetachedFromWindow() {
        clearRipple()
        removeCallbacks(clearHighlightRunnable)
        clearHighlight()
        super.onDetachedFromWindow()
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
        rippleRadius = minOf(maxRippleRadius, minOf(singleCharWidth, h) / 2f - rippleInset)
    }

    override fun onMeasure(width: Int, height: Int) {
        val widthSize = MeasureSpec.getSize(width)
        val heightSpec = MeasureSpec.makeMeasureSpec(widthSize / count, MeasureSpec.EXACTLY)
        super.onMeasure(width, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        ripple.draw(canvas)
        codePoints.forEachIndexed { index, codePoint ->
            val charCoordX = charCoordsX[index]
            val charPivotX = charCoordsX[index]
            if (charRipples[index]) {
                canvas.drawCircle(charPivotX, charPivotY, rippleRadius, ripplePaint)
            }
            drawChar(canvas, codePoint, charCoordX, charCoordY)
        }
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
        val PRESSED_STATE = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
        val EMPTY_STATE = intArrayOf()
    }
}