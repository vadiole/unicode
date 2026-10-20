package vadiole.unicode.ui.table.search

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.dp

class SearchResultView(
    context: Context,
    adapter: Adapter,
    private val delegate: Delegate,
) : CollectionView(context) {

    interface Delegate {
        fun onStartScrolling()
        fun onScrollProgressChanged(progress: Float, canScroll: Boolean)
    }

    private val searchLayoutManager = LinearLayoutManager(context)
    private val itemDecoration = CollectionItemDecoration(leftPadding = 14f.dp(context))

    init {
        recycledViewPool.setMaxRecycledViews(0, 32)
        layoutManager = searchLayoutManager
        setItemViewCacheSize(8)
        setAdapter(adapter)
        clipChildren = true
        addItemDecoration(itemDecoration)
    }

    override fun onScrollStateChanged(state: Int) {
        if (state == SCROLL_STATE_DRAGGING) {
            delegate.onStartScrolling()
        }
    }

    override fun onScrolled(dx: Int, dy: Int) {
        updateScrollProgress()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateScrollProgress()
    }

    private fun updateScrollProgress() {
        val scrollable = computeVerticalScrollRange() - computeVerticalScrollExtent()
        val progress = if (scrollable > 0) (computeVerticalScrollOffset().toFloat() / scrollable).coerceIn(0f, 1f) else 0f
        delegate.onScrollProgressChanged(progress, canScrollVertically(-1) || canScrollVertically(1))
    }

    fun scrollToProgress(progress: Float) {
        val itemCount = adapter?.itemCount ?: return
        if (itemCount <= 0) return
        val range = computeVerticalScrollRange()
        val scrollable = range - computeVerticalScrollExtent()
        val rowHeight = range.toFloat() / itemCount
        if (scrollable <= 0 || rowHeight <= 0f) return
        val target = scrollable * progress.coerceIn(0f, 1f)
        val position = (target / rowHeight).toInt().coerceAtMost(itemCount - 1)
        val offset = (target - position * rowHeight).toInt()
        searchLayoutManager.scrollToPositionWithOffset(position, -offset)
    }
}
