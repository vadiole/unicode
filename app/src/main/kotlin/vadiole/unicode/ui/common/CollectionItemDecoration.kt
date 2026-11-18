package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Paint
import vadiole.unicode.R

class CollectionItemDecoration(private val leftPadding: Float = 0f) : RecyclerView.ItemDecoration() {
    private val bounds = Rect()
    private var dividerPaint: Paint? = null
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerPaint = dividerPaint ?: Paint().apply {
            strokeWidth = 1f
            color = parent.context.getColor(R.color.windowDivider)
        }.also { dividerPaint = it }
        val isRtl = parent.layoutDirection == RecyclerView.LAYOUT_DIRECTION_RTL
        val start = parent.paddingLeft.toFloat()
        val end = (parent.width - parent.paddingRight).toFloat()
        val left = if (isRtl) start else start + leftPadding
        val right = if (isRtl) end - leftPadding else end
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) == state.itemCount - 1) continue
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val yCoord: Float = bounds.bottom + child.translationY
            c.drawLine(left, yCoord, right, yCoord, dividerPaint)
        }
    }
}