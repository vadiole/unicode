package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat.OPAQUE
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.roundToInt

class ScrollbarDrawable : Drawable() {

    private val scrollBarPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
    }
    private var range = 0
    private var offset = 0
    private var extent = 0
    private var thumbRectCache = RectF()
    private var changed = false
    private var rangeChanged = false

    fun setParameters(range: Int, offset: Int, extent: Int) {
        if (this.range != range || this.offset != offset || this.extent != extent) {
            rangeChanged = true
        }
        this.range = range
        this.offset = offset
        this.extent = extent
    }

    fun setColor(color: Int) {
        scrollBarPaint.color = color
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        changed = true
    }

    override fun draw(canvas: Canvas) {
        val extent: Int = extent
        val range: Int = range
        var drawThumb = true
        if (extent <= 0 || range <= extent) {
            drawThumb = false
        }
        val drawableBounds = bounds
        if (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                canvas.quickReject(
                    drawableBounds.left.toFloat(),
                    drawableBounds.top.toFloat(),
                    drawableBounds.right.toFloat(),
                    drawableBounds.bottom.toFloat(),
                )
            } else {
                @Suppress("DEPRECATION")
                canvas.quickReject(
                    drawableBounds.left.toFloat(),
                    drawableBounds.top.toFloat(),
                    drawableBounds.right.toFloat(),
                    drawableBounds.bottom.toFloat(),
                    Canvas.EdgeType.AA,
                )
            }
        ) {
            return
        }

        if (drawThumb) {
            val trackHeight = drawableBounds.height()
            val thickness = drawableBounds.width()

            val minThumbHeight = thickness * 8
            val ratio = extent.toFloat() / range
            val thumbHeight = ((trackHeight - minThumbHeight) * ratio + minThumbHeight).roundToInt()
            val thumbTop = ((trackHeight - thumbHeight).toFloat() * offset / (range - extent)).roundToInt()

            drawThumb(canvas, drawableBounds, thumbTop, thumbHeight)
        }
    }

    private fun drawThumb(canvas: Canvas, bounds: Rect, offset: Int, length: Int) {
        val thumbRect: RectF = thumbRectCache
        val changed = rangeChanged || changed
        if (changed) {
            thumbRect.set(bounds.left.toFloat(), (bounds.top + offset).toFloat(), bounds.right.toFloat(), (bounds.top + offset + length).toFloat())
        }
        canvas.drawRoundRect(thumbRect, bounds.width().toFloat(), bounds.width().toFloat(), scrollBarPaint)
    }

    override fun setAlpha(alpha: Int) {
        scrollBarPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        scrollBarPaint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = OPAQUE

    override fun toString(): String {
        return "ScrollBarDrawable: range=$range offset=$offset extent=$extent"
    }
}
