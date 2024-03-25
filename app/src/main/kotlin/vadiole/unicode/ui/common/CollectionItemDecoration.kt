package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.utils.extension.getDividerPaint

class CollectionItemDecoration(private val leftPadding: Float = 0f) : RecyclerView.ItemDecoration() {
    private val bounds = Rect()
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val dividerPaint = parent.getDividerPaint()
        val right = parent.width.toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val yCoord: Float = bounds.bottom + child.translationY
            c.drawLine(leftPadding, yCoord, right, yCoord, dividerPaint)
        }
    }
}