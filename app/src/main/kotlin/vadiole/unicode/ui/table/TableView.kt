package vadiole.unicode.ui.table

import android.content.Context
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.ui.common.CollectionView

import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.extension.setPaddingHorizontal

class TableView(
    context: Context,
    private val adapter: TableAdapter,
    private val spanCount: Int,
    private val delegate: Delegate,
) : CollectionView(context) {
    private val tableLayoutManager = LinearLayoutManager(context)

    init {
        recycledViewPool.setMaxRecycledViews(0, spanCount * 6)
        layoutManager = tableLayoutManager
        setItemViewCacheSize(spanCount * 2)
        setAdapter(adapter)
        setPaddingHorizontal(8.dp(context))
    }

    override fun onScrolled(dx: Int, dy: Int) {
        val position = tableLayoutManager.findFirstVisibleItemPosition()
        val block = adapter.getBlock(position * spanCount)
        if (block != null) {
            delegate.onBlockChanged(block.name)
        }
        delegate.onScrollProgressChanged(getScrollProgress())
    }

    fun getScrollProgress(): Float {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val offset = computeVerticalScrollOffset()
        val scrollable = range - extent
        if (scrollable <= 0) return 0f
        return (offset.toFloat() / scrollable).coerceIn(0f, 1f)
    }

    fun scrollToProgress(progress: Float, precisionLevel: Int) {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return
        // At the slowest drag speed one finger pixel spans a fraction of a row, so row-snap
        // becomes visible stutter — land on an exact pixel offset instead.
        if (isOffsetPrecision(precisionLevel)) {
            val range = computeVerticalScrollRange()
            val extent = computeVerticalScrollExtent()
            val scrollable = range - extent
            val rowHeight = range / itemCount
            if (scrollable > 0 && rowHeight > 0) {
                val targetPx = (scrollable * progress.coerceIn(0f, 1f)).toLong()
                val row = (targetPx / rowHeight).toInt().coerceAtMost(itemCount - 1)
                val intraRowOffset = (targetPx - row.toLong() * rowHeight).toInt()
                tableLayoutManager.scrollToPositionWithOffset(row, -intraRowOffset)
                return
            }
        }
        val targetPosition = (itemCount * progress).toInt().coerceIn(0, itemCount - 1)
        tableLayoutManager.scrollToPositionWithOffset(targetPosition, 0)
    }

    private fun isOffsetPrecision(precisionLevel: Int): Boolean = precisionLevel == 2

    fun scrollToPositionInCenter(row: Int, indexInRow: Int) {
        val offset = measuredHeight / 2
        tableLayoutManager.scrollToPositionWithOffset(row, offset)
        doOnNextLayout {
            val cell = tableLayoutManager.findViewByPosition(row) as? CharRow ?: return@doOnNextLayout
            cell.highlightChar(indexInRow)
        }
    }

    fun scrollToPositionTop(row: Int) {
        tableLayoutManager.scrollToPositionWithOffset(row, 0)
    }

    interface Delegate {
        fun onBlockChanged(name: String?)
        fun onScrollProgressChanged(progress: Float)
    }

}