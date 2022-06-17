package vadiole.unicode.ui.table.search

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_windowDivider
import vadiole.unicode.utils.extension.dp

class SearchResultView(
    context: Context,
    adapter: Adapter,
    private val delegate: Delegate,
) : CollectionView(context), ThemeDelegate {

    interface Delegate {
        fun onStartScrolling()
    }

    private val searchLayoutManager = LinearLayoutManager(context)
    private val dividerDrawable = object : ColorDrawable() {
        override fun getIntrinsicHeight(): Int {
            return 1
        }

        override fun getPadding(padding: Rect): Boolean {
            padding.set(16.dp(context), 0, 0, 0)
            return true
        }
    }
    private val itemDecoration = CollectionItemDecoration(leftPadding = 14f.dp(context))

    init {
        recycledViewPool.setMaxRecycledViews(0, 32)
        layoutManager = searchLayoutManager
        setItemViewCacheSize(8)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
        themeManager.observe(this)
    }

    override fun onScrollStateChanged(state: Int) {
        if (state == SCROLL_STATE_DRAGGING) {
            delegate.onStartScrolling()
        }
    }

    override fun applyTheme() {
        dividerDrawable.color = themeManager.getColor(key_windowDivider)
    }
}