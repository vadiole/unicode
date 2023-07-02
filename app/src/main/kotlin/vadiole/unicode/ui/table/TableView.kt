package vadiole.unicode.ui.table

import android.content.Context
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.ScrollbarDrawable
import vadiole.unicode.ui.common.VerticalScrollBarItemDecoration
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_dialogSurfacePressed
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.setPaddingHorizontal

class TableView(
    context: Context,
    private val adapter: TableAdapter,
    private val spanCount: Int,
    private val delegate: Delegate,
) : CollectionView(context), ThemeDelegate {
    private val tableLayoutManager = LinearLayoutManager(context)
    private val itemDecoration = TableItemDecoration()
    private val scrollbarDrawable = ScrollbarDrawable()
    private val scrollBarItemDecoration = VerticalScrollBarItemDecoration(
        recyclerView = this,
        scrollbarDrawable = scrollbarDrawable,
        scrollBarWidth = 4.dp(context),
    )

    init {
        recycledViewPool.setMaxRecycledViews(0, spanCount * 6)
        layoutManager = tableLayoutManager
        setItemViewCacheSize(spanCount * 2)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
        addItemDecoration(scrollBarItemDecoration)
        setPaddingHorizontal(8.dp(context))
        themeManager.observe(this)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        val position = tableLayoutManager.findFirstVisibleItemPosition()
        val block = adapter.getBlock(position * spanCount)
        delegate.onBlockChanged(block?.name)
    }

    fun scrollToPositionInCenter(row: Int, indexInRow: Int) {
        val offset = measuredHeight / 2
        tableLayoutManager.scrollToPositionWithOffset(row, offset)
        doOnNextLayout {
            val cell = tableLayoutManager.findViewByPosition(row) as CharRow
            cell.highlightChar(indexInRow)
        }
    }

    interface Delegate {
        fun onBlockChanged(name: String?)
    }

    override fun applyTheme() {
        scrollbarDrawable.setColor(themeManager.getColor(key_dialogSurfacePressed))
    }
}
