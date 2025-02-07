package vadiole.unicode.ui.table

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import kotlin.math.floor
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.utils.extension.dp

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
                cancelClick(index)
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

    init {
        charPaint.color = context.getColor(R.color.windowTextPrimary)
        abbreviationPaint.color = ColorUtils.setAlphaComponent(context.getColor(R.color.windowTextPrimary), 128)
        ripplePaint.color = context.getColor(R.color.windowSurfacePressed)
        charPaint.textSize = charSize
        abbreviationPaint.textSize = abbreviationSize
        isClickable = true
        isFocusable = true
    }

    fun bind(codePoints: CodePointArray, abbreviations: Map<CodePoint, String>) {
        this.codePoints = codePoints
        this.abbreviations = abbreviations
        charRipples.fill(false)
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
                actionDownIndex = index
                charRipples[index] = true
                postDelayed(longClickRunnable, longClickDuration)
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex == index) {
                        val codePoint = codePoints[index]
                        delegate.onClick(codePoint)
                    }
                    cancelClick(actionDownIndex)
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

    private fun cancelClick(index: Int) {
        charRipples[index] = false
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        invalidate()
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
            }
            drawChar(canvas, codePoint, charCoordX, charCoordY)
        }
    }

    private fun drawChar(canvas: Canvas, codePoint: CodePoint, charCoordX: Float, charCoordY: Float) {
        val abbreviation = abbreviations[codePoint]
        if (abbreviation != null) {
            canvas.drawText(abbreviation, charCoordX, abbreviationCoordY, abbreviationPaint)
        } else {
            Character.UnicodeBlock.SPECIALS
            canvas.drawText(codePoint.char, charCoordX, charCoordY, charPaint)
        }
    }

    override fun hasOverlappingRendering() = false

    interface Delegate {
        fun onClick(codePoint: CodePoint)
        fun onLongClick(codePoint: CodePoint)
    }
}