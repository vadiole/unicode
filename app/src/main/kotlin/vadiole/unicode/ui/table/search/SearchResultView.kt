package vadiole.unicode.ui.table.search

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.R

import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.ScrollbarDrawable
import vadiole.unicode.ui.common.VerticalScrollBarItemDecoration

import vadiole.unicode.ui.common.dp

class SearchResultView(
    context: Context,
    adapter: Adapter,
    private val delegate: Delegate,
) : CollectionView(context) {

    interface Delegate {
        fun onStartScrolling()
    }

    private val searchLayoutManager = LinearLayoutManager(context)
    private val scrollbarDrawable = ScrollbarDrawable()
    private val scrollBars = VerticalScrollBarItemDecoration(
        recyclerView = this,
        scrollbarDrawable = scrollbarDrawable,
        scrollBarWidth = 4.dp(context),
    )
    private val itemDecoration = CollectionItemDecoration(leftPadding = 14f.dp(context))

    init {
        recycledViewPool.setMaxRecycledViews(0, 32)
        recycledViewPool.setMaxRecycledViews(2, 16)
        layoutManager = searchLayoutManager
        setItemViewCacheSize(8)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
        addItemDecoration(scrollBars)
        scrollbarDrawable.setColor(this.context.getColor(R.color.scrollIndicator))
    }

    fun setGridMode(isGrid: Boolean) {
        if (isGrid) {
            removeItemDecoration(itemDecoration)
            setPadding(8.dp(context), 0, 8.dp(context), paddingBottom)
        } else {
            if (itemDecorationCount == 1) addItemDecoration(itemDecoration)
            setPadding(0, 0, 0, paddingBottom)
        }
    }

    override fun onScrollStateChanged(state: Int) {
        if (state == SCROLL_STATE_DRAGGING) {
            delegate.onStartScrolling()
        }
    }
}