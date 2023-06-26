package vadiole.unicode.ui.common

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class CollectionView(context: Context) : RecyclerView(context) {
    init {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        clipToPadding = false
        clipChildren = false
        itemAnimator = null
        super.setHasFixedSize(true)
    }

    abstract class Adapter : RecyclerView.Adapter<Cell>()

    class Cell(cell: View) : ViewHolder(cell)
}