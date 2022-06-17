package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import vadiole.unicode.utils.extension.dp
import kotlin.math.min

open class SimpleTextView(context: Context) : View(context) {
    var text: String = ""
        set(value) {
            field = value
            postInvalidate()
        }
    var textSize: Float
        get() = textPaint.textSize
        set(value) {
            textPaint.textSize = value
            requestLayout()
        }
    var textColor: Int
        get() = textPaint.color
        set(value) {
            textPaint.color = value
            postInvalidate()
        }

    var gravity: Int = Gravity.CENTER
        set(value) {
            field = value
            onSizeChanged(measuredWidth, measuredHeight, 0, 0)
            invalidate()
        }

    var typeface: Typeface?
        get() {
            return textPaint.typeface
        }
        set(value) {
            textPaint.typeface = value
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 17f.dp(context)
        letterSpacing = -0.02f
        isSubpixelText = true
    }
    private var textPositionY: Float = 0f
    private var textPositionX: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        textPositionY = 0.5f * (h - textPaint.descent() - textPaint.ascent())
        when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.CENTER_HORIZONTAL -> {
                textPaint.textAlign = Paint.Align.CENTER
                textPositionX = (w - paddingLeft - paddingRight) / 2f + paddingLeft
            }
            Gravity.LEFT -> {
                textPaint.textAlign = Paint.Align.LEFT
                textPositionX = paddingLeft.toFloat()
            }
            Gravity.RIGHT -> {
                textPaint.textAlign = Paint.Align.RIGHT
                textPositionX = w - paddingRight.toFloat()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)

        // wrap content
        if (widthMode == MeasureSpec.AT_MOST) {
            val textWidth = textPaint.measureText(text).toInt()
            val totalWidth = textWidth + paddingLeft + paddingRight
            val finalWidth = min(availableWidth, totalWidth)
            val newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY)
            setMeasuredDimension(newWidthMeasureSpec, heightMeasureSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(text, textPositionX, textPositionY, textPaint)
    }

    override fun hasOverlappingRendering() = false
}