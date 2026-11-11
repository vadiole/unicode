package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.ui.extension.getDividerPaint

class CollectionItemDecoration(private val leftPadding: Float = 0f) : RecyclerView.ItemDecoration() {
    private val bounds = Rect()
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerPaint = parent.getDividerPaint()
        val isRtl = parent.layoutDirection == RecyclerView.LAYOUT_DIRECTION_RTL
        val left = if (isRtl) 0f else leftPadding
        val right = if (isRtl) parent.width - leftPadding else parent.width.toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) == state.itemCount - 1) continue
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val yCoord: Float = bounds.bottom + child.translationY
            c.drawLine(left, yCoord, right, yCoord, dividerPaint)
        }
    }
}