package vadiole.unicode.ui.table

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.ui.common.CollectionView

class TableView(
    context: Context,
    private val adapter: TableAdapter,
    private val spanCount: Int,
    private val delegate: Delegate,
) : CollectionView(context) {
    private val tableLayoutManager = LinearLayoutManager(context)
    private val itemDecoration = TableItemDecoration()

    init {
        recycledViewPool.setMaxRecycledViews(0, spanCount * 6)
        layoutManager = tableLayoutManager
        setItemViewCacheSize(spanCount * 2)
        setHasFixedSize(true)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
        clipChildren = false
    }

    override fun onScrolled(dx: Int, dy: Int) {
        val position = tableLayoutManager.findFirstVisibleItemPosition()
        val block = adapter.getBlock(position * spanCount)
        delegate.onBlockChanged(block?.name)
    }

    interface Delegate {
        fun onBlockChanged(name: String?)
    }
}