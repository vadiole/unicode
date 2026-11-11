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
import vadiole.unicode.ui.common.addSquircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.fontRegular
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.rippleDrawable
import vadiole.unicode.ui.common.wrapContent

class BlockSelectorCell(context: Context) : TextView(context) {
    private val iconStart = ICON_START.dp(context)
    private val iconSize = ICON_SIZE.dp(context)
    private val iconRadius = ICON_RADIUS.dp(context)
    private val iconInner = iconSize - 2 * ICON_PADDING.dp(context)
    private val gridGap = GRID_GAP.dp(context)
    private val gridCell = (iconInner - gridGap) / 2f
    private val iconRect = RectF()
    val iconCenterX: Float
        get() = iconRect.centerX()
    private val iconPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BORDER_WIDTH.dp(context)
        color = context.getColor(R.color.floatingBorder)
    }
    private val largePaint = createGlyphPaint(context, LARGE_GLYPH)
    private val gridPaint = createGlyphPaint(context, GRID_GLYPH)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.characterAbbreviation)
        textSize = LABEL_SIZE.dp(context)
        isSubpixelText = true
    }
    private val glyphBounds = Rect()
    private val glyphChars = CharArray(BlockList.ICON_SLOTS * GLYPH_STRIDE)
    private val glyphLengths = IntArray(BlockList.ICON_SLOTS)
    private val glyphCentersX = FloatArray(BlockList.ICON_SLOTS)
    private val glyphCentersY = FloatArray(BlockList.ICON_SLOTS)
    private val glyphOffsetsX = FloatArray(BlockList.ICON_SLOTS)
    private val glyphOffsetsY = FloatArray(BlockList.ICON_SLOTS)
    private val glyphScales = FloatArray(BlockList.ICON_SLOTS)
    private var glyphCount = 0
    private var glyphPaint = largePaint
    private var label: String? = null
    private var labelOffsetX = 0f
    private var labelOffsetY = 0f
    private var labelScale = 1f

    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
        minHeight = 48.dp(context)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        typeface = fontRegular
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

    fun bind(name: String, blockList: BlockList, position: Int) {
        text = name
        val type = blockList.types[position].toInt()
        val offset = position * BlockList.ICON_SLOTS
        val icons = blockList.icons
        var available = 0
        while (type != BlockList.TYPE_NOTHING && available < BlockList.ICON_SLOTS && icons[offset + available] != 0) available++
        glyphCount = 0
        label = null
        when {
            available == 0 -> bindLabel(blockList.labels[position])
            available < BlockList.ICON_SLOTS || type == BlockList.TYPE_SCRIPT -> bindGlyphs(1, blockList, offset)
            else -> bindGlyphs(BlockList.ICON_SLOTS, blockList, offset)
        }
        invalidate()
    }

    private fun bindLabel(text: String?) {
        if (text == null) return
        label = text
        val width = labelPaint.measureText(text)
        labelPaint.getTextBounds(text, 0, text.length, glyphBounds)
        labelOffsetX = -width / 2f
        labelOffsetY = -(labelPaint.ascent() + labelPaint.descent()) / 2f
        labelScale = minOf(1f, iconInner / width, iconInner / maxOf(1, glyphBounds.height()))
    }

    private fun bindGlyphs(slots: Int, blockList: BlockList, offset: Int) {
        val single = slots == 1
        val paint = if (single) largePaint else gridPaint
        val box = if (single) iconInner else gridCell
        val origin = (iconSize - iconInner) / 2f
        glyphPaint = paint
        for (index in 0 until slots) {
            val codePoint = blockList.icons[offset + index]
            val start = index * GLYPH_STRIDE
            var length = 0
            if (blockList.iconFlags[offset + index].toInt() and BlockList.FLAG_MARK != 0) glyphChars[start + length++] = BlockList.MARK_BASE
            length += Character.toChars(codePoint, glyphChars, start + length)
            glyphLengths[index] = length
            glyphCentersX[index] = if (single) iconSize / 2f else origin + gridCell / 2f + (index % 2) * (gridCell + gridGap)
            glyphCentersY[index] = if (single) iconSize / 2f else origin + gridCell / 2f + (index / 2) * (gridCell + gridGap)
            paint.getTextBounds(glyphChars, start, length, glyphBounds)
            if (glyphBounds.isEmpty) {
                glyphOffsetsX[index] = -paint.measureText(glyphChars, start, length) / 2f
                glyphOffsetsY[index] = -(paint.ascent() + paint.descent()) / 2f
                glyphScales[index] = 1f
            } else {
                glyphOffsetsX[index] = -glyphBounds.exactCenterX()
                glyphOffsetsY[index] = -glyphBounds.exactCenterY()
                glyphScales[index] = minOf(1f, box / glyphBounds.width(), box / glyphBounds.height())
            }
        }
        glyphCount = slots
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) w - iconStart - iconSize else iconStart
        val top = (h - iconSize) / 2f
        iconRect.set(left, top, left + iconSize, top + iconSize)
        val inset = borderPaint.strokeWidth / 2f
        iconRect.inset(inset, inset)
        iconPath.rewind()
        iconPath.addSquircle(iconRect, iconRadius)
        iconRect.inset(-inset, -inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(iconPath, borderPaint)
        val label = label
        if (label != null) {
            val centerX = iconRect.centerX()
            val centerY = iconRect.centerY()
            val save = canvas.save()
            canvas.scale(labelScale, labelScale, centerX, centerY)
            canvas.drawText(label, centerX + labelOffsetX, centerY + labelOffsetY, labelPaint)
            canvas.restoreToCount(save)
        }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (index in 0 until glyphCount) {
            val centerX = if (rtl) iconRect.right - glyphCentersX[index] else iconRect.left + glyphCentersX[index]
            val centerY = iconRect.top + glyphCentersY[index]
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
        private const val ICON_START = 16f
        private const val ICON_SIZE = 36f
        private const val ICON_RADIUS = 6f
        private const val ICON_PADDING = 2f
        private const val GRID_GAP = 1f
        private const val BORDER_WIDTH = 0.5f
        private const val LARGE_GLYPH = 20f
        private const val GRID_GLYPH = 13f
        private const val LABEL_SIZE = 11f

        fun textStart(context: Context) = (ICON_START + ICON_SIZE + 12f).dp(context).toInt()

        private fun createGlyphPaint(context: Context, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.windowTextSecondary)
            textSize = size.dp(context)
            isSubpixelText = true
        }
    }
}
