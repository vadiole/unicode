package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.ceil
import vadiole.unicode.R

class CharInfoView(context: Context) : View(context) {
    private val backgroundDrawable = SquircleDrawable(12.dp(context))
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TEXT_SECONDARY.sp(context)
        typeface = mono_regular
        style = Paint.Style.FILL
        isSubpixelText = true
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TEXT_CAPTION.sp(context)
        typeface = mono_regular
        style = Paint.Style.FILL
        isSubpixelText = true
    }
    private val valueTextSize = valuePaint.textSize
    private val valueMetrics = valuePaint.fontMetrics
    private val nameMetrics = namePaint.fontMetrics
    private val lineGap = 5f.dp(context)
    private val contentHeight = valueMetrics.descent - valueMetrics.ascent + lineGap + nameMetrics.descent - nameMetrics.ascent
    private val minCellHeight = 56.dp(context)
    private val verticalPadding = 6f.dp(context)
    private val horizontalPadding = 6f.dp(context)
    private var viewCenterX: Float = 0f
    private var valueCoordinateY = 0f
    private var nameCoordinateY = 0f
    var name: String = context.getString(R.string.details_info_code)
        set(value) {
            field = value
            invalidate()
        }
    var value: String = "U+0041"
        set(value) {
            field = value
            fitValue()
        }

    init {
        backgroundDrawable.colors = run {
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(-android.R.attr.state_pressed),
                ),
                intArrayOf(
                    this.context.getColor(R.color.layer1Pressed),
                    this.context.getColor(R.color.layer1),
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
        val height = maxOf(minCellHeight, ceil(contentHeight + verticalPadding * 2).toInt())
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewCenterX = 0.5f * w
        valueCoordinateY = (h - contentHeight) / 2f - valueMetrics.ascent
        nameCoordinateY = valueCoordinateY + valueMetrics.descent + lineGap - nameMetrics.ascent
        fitValue()
    }

    private fun fitValue() {
        valuePaint.textSize = valueTextSize
        val available = width - horizontalPadding * 2
        val textWidth = valuePaint.measureText(value)
        if (available > 0 && textWidth > available) {
            valuePaint.textSize = valueTextSize * available / textWidth
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(value, viewCenterX, valueCoordinateY, valuePaint)
        canvas.drawText(name, viewCenterX, nameCoordinateY, namePaint)
    }
}