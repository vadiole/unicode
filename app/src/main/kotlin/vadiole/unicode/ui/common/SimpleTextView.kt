package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

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
            postInvalidate()
        }
    var textColor: Int
        get() = textPaint.color
        set(value) {
            textPaint.color = value
            postInvalidate()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private var textPositionY: Float = 0f
    private var textPositionX: Float = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        textPositionX = w / 2f
        textPositionY = 0.5f * (h - textPaint.descent() - textPaint.ascent())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(text, textPositionX, textPositionY, textPaint)
    }

    override fun hasOverlappingRendering() = false
}