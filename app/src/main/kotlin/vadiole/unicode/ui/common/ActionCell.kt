package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.DrawableRes
import kotlin.math.ceil
import vadiole.unicode.R

open class ActionCell(
    context: Context,
    name: String,
    topItem: Boolean = false,
    bottomItem: Boolean = false,
    middleItem: Boolean = false,
) : TextView(context) {
    private val radius = 13.dp(context).toFloat()
    private val valueGap = 16.dp(context)
    private val iconGap = 4.dp(context)
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val radii = FloatArray(4)
    private val shapeRect = RectF()
    protected val shapePath = Path()
    private var valueWidth = 0
    private var iconWidth = 0

    var value: String? = null
        set(value) {
            field = value
            val width = value?.let { valuePaint.measureText(it).toInt() } ?: 0
            if (width != valueWidth) {
                valueWidth = width
                requestLayout()
            }
            invalidate()
        }

    var valueScale = 1f
        set(value) {
            field = value
            invalidate()
        }

    var valueMaxScale = 1f
        set(value) {
            field = value
            requestLayout()
        }

    init {
        compoundDrawableTintList = this.context.getColorStateList(R.color.windowTextPrimary)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        text = name
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        minHeight = 48.dp(context)
        includeFontPadding = false
        typeface = font_regular
        textSize = TEXT_BODY
        isClickable = true
        isFocusable = true
        setPadding(16.dp(context), 10.dp(context), 16.dp(context), 10.dp(context))
        valuePaint.set(paint)
        valuePaint.color = context.getColor(R.color.windowTextSecondary)
        setCorners(
            top = topItem || !bottomItem && !middleItem,
            bottom = bottomItem || !topItem && !middleItem,
        )
    }

    fun setCorners(top: Boolean, bottom: Boolean) {
        val t = if (top) radius else 0f
        val b = if (bottom) radius else 0f
        radii[0] = t
        radii[1] = t
        radii[2] = b
        radii[3] = b
        val r = radius.toInt()
        fun shape() = SquircleDrawable(0, if (top) r else 0, if (top) r else 0, if (bottom) r else 0, if (bottom) r else 0)
        background = context.rippleDrawable(
            shape().apply { colors = ColorStateList.valueOf(context.getColor(R.color.layer1)) },
            shape().apply { colors = ColorStateList.valueOf(Color.WHITE) },
        )
        updateShapePath()
    }

    fun setValueColor(color: Int) {
        valuePaint.color = color
        invalidate()
    }

    fun setIcon(@DrawableRes iconId: Int) {
        setCompoundDrawablesWithIntrinsicBounds(0, 0, iconId, 0)
        iconWidth = context.getDrawable(iconId)?.intrinsicWidth ?: 0
    }

    fun setChevron() {
        setIcon(R.drawable.ic_chevron_right)
        compoundDrawableTintList = context.getColorStateList(R.color.windowTextSecondary)
    }

    override fun getCompoundPaddingRight(): Int {
        val extra = if (valueWidth > 0) ceil(valueWidth * valueMaxScale).toInt() + valueGap + if (iconWidth > 0) iconGap else 0 else 0
        return super.getCompoundPaddingRight() + extra
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShapePath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = value ?: return
        val right = width - paddingRight - if (iconWidth > 0) iconWidth + iconGap else 0
        val centerY = (paddingTop + height - paddingBottom) / 2f
        val y = centerY - (valuePaint.descent() + valuePaint.ascent()) / 2f
        if (valueScale == 1f) {
            canvas.drawText(value, (right - valueWidth).toFloat(), y, valuePaint)
            return
        }
        val save = canvas.save()
        canvas.scale(valueScale, valueScale, right.toFloat(), centerY)
        canvas.drawText(value, (right - valueWidth).toFloat(), y, valuePaint)
        canvas.restoreToCount(save)
    }

    private fun updateShapePath() {
        shapeRect.set(0f, 0f, width.toFloat(), height.toFloat())
        shapePath.rewind()
        shapePath.addSquircle(shapeRect, radii)
    }
}
