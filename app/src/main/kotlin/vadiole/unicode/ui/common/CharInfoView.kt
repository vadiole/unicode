package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import vadiole.unicode.R


import vadiole.unicode.utils.extension.dp

class CharInfoView(context: Context) : View(context) {
    private val backgroundDrawable = SquircleDrawable(12.dp(context))
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f.dp(context)
        typeface = roboto_regular
        style = Paint.Style.FILL
        isSubpixelText = true
        letterSpacing = 0.02f
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f.dp(context)
        typeface = roboto_regular
        style = Paint.Style.FILL
        isSubpixelText = true
        letterSpacing = 0.02f
    }
    private var viewCenterX: Float = 0f
    private val valueCoordinateY = 25f.dp(context)
    private val nameCoordinateY = 44f.dp(context)
    var name: String = "Code"
        set(value) {
            field = value
            invalidate()
        }
    var value: String = "U+0041"
        set(value) {
            field = value
            invalidate()
        }

    init {
        backgroundDrawable.colors = run {
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(-android.R.attr.state_pressed),
                ),
                intArrayOf(
                    this.context.getColor(R.color.dialogSurfacePressed),
                    this.context.getColor(R.color.dialogSurface),
                )
            )
        }
        valuePaint.color = this.context.getColor(R.color.windowTextPrimary)
        namePaint.color = this.context.getColor(R.color.windowTextSecondary)
        isClickable = true
        isFocusable = true
        background = backgroundDrawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(56.dp(context), MeasureSpec.EXACTLY))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewCenterX = 0.5f * w
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(value, viewCenterX, valueCoordinateY, valuePaint)
        canvas.drawText(name, viewCenterX, nameCoordinateY, namePaint)
    }
}