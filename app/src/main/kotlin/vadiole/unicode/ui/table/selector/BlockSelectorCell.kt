package vadiole.unicode.ui.table.selector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.data.BlockList
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.addSquircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.font_regular
import vadiole.unicode.ui.common.rippleDrawable
import vadiole.unicode.ui.common.sp
import vadiole.unicode.ui.common.wrapContent

class BlockSelectorCell(context: Context) : TextView(context) {
    private val iconStart = 16.dp(context)
    private val iconSize = 36f.dp(context)
    private val iconRadius = 10f.dp(context)
    private val iconRect = RectF()
    private val iconPath = Path()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.layer0)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.windowTextSecondary)
        textSize = TEXT_SECONDARY.sp(context)
        isSubpixelText = true
    }
    private val glyphMaxExtent = 16f.dp(context)
    private val glyphBounds = Rect()
    private val glyphChars = CharArray(BlockList.ICON_SIZE * GLYPH_STRIDE)
    private val glyphLengths = IntArray(BlockList.ICON_SIZE)
    private val glyphOffsetsX = FloatArray(BlockList.ICON_SIZE)
    private val glyphOffsetsY = FloatArray(BlockList.ICON_SIZE)
    private val glyphScales = FloatArray(BlockList.ICON_SIZE)
    private var glyphCount = 0

    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
        minHeight = 48.dp(context)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        typeface = font_regular
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPaddingRelative(textStart(context), 4.dp(context), 16.dp(context), 4.dp(context))
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        background = context.rippleDrawable(
            content = ColorDrawable(context.getColor(R.color.windowSurface)),
            mask = ColorDrawable(Color.WHITE),
        )
    }

    fun bind(name: String, icons: IntArray, offset: Int) {
        text = name
        glyphCount = 0
        while (glyphCount < BlockList.ICON_SIZE && icons[offset + glyphCount] != 0) {
            val index = glyphCount
            val codePoint = icons[offset + index]
            val start = index * GLYPH_STRIDE
            var length = 0
            if (BlockList.isMark(codePoint)) glyphChars[start + length++] = BlockList.MARK_BASE
            length += Character.toChars(codePoint, glyphChars, start + length)
            glyphLengths[index] = length
            glyphPaint.getTextBounds(glyphChars, start, length, glyphBounds)
            if (glyphBounds.isEmpty) {
                glyphOffsetsX[index] = -glyphPaint.measureText(glyphChars, start, length) / 2f
                glyphOffsetsY[index] = -(glyphPaint.ascent() + glyphPaint.descent()) / 2f
                glyphScales[index] = 1f
            } else {
                glyphOffsetsX[index] = -glyphBounds.exactCenterX()
                glyphOffsetsY[index] = -glyphBounds.exactCenterY()
                val extent = maxOf(glyphBounds.width(), glyphBounds.height())
                glyphScales[index] = minOf(1f, glyphMaxExtent / extent)
            }
            glyphCount++
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) w - iconStart - iconSize else iconStart.toFloat()
        val top = (h - iconSize) / 2f
        iconRect.set(left, top, left + iconSize, top + iconSize)
        iconPath.rewind()
        iconPath.addSquircle(iconRect, iconRadius)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(iconPath, iconPaint)
        val quarter = iconSize / 4f
        for (index in 0 until glyphCount) {
            val centerX = iconRect.left + quarter * (1 + 2 * (index % 2))
            val centerY = iconRect.top + quarter * (1 + 2 * (index / 2))
            val scale = glyphScales[index]
            if (scale < 1f) {
                val save = canvas.save()
                canvas.scale(scale, scale, centerX, centerY)
                drawGlyph(canvas, index, centerX, centerY)
                canvas.restoreToCount(save)
            } else {
                drawGlyph(canvas, index, centerX, centerY)
            }
        }
        super.onDraw(canvas)
    }

    private fun drawGlyph(canvas: Canvas, index: Int, centerX: Float, centerY: Float) {
        canvas.drawText(glyphChars, index * GLYPH_STRIDE, glyphLengths[index], centerX + glyphOffsetsX[index], centerY + glyphOffsetsY[index], glyphPaint)
    }

    companion object {
        private const val GLYPH_STRIDE = 3

        fun textStart(context: Context) = (16 + 36 + 12).dp(context)
    }
}
