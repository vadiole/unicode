package vadiole.unicode.ui.table

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import vadiole.unicode.ui.components.CollectionView

class TableView(
    context: Context,
    adapter: Adapter,
    spanCount: Int,
) : CollectionView(context) {
    init {
        recycledViewPool.setMaxRecycledViews(0, spanCount * spanCount * 6)
        layoutManager = GridLayoutManager(context, spanCount)
        setItemViewCacheSize(spanCount * spanCount * 2)
        setHasFixedSize(true)
        setAdapter(adapter)
    }
}