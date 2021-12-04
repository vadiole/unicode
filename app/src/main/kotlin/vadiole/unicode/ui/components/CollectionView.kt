package vadiole.unicode.ui.components

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class CollectionView(context: Context) : RecyclerView(context) {
    init {
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        itemAnimator = null
    }

    abstract class Adapter : RecyclerView.Adapter<Cell>()

    class Cell(cell: View) : ViewHolder(cell)
}