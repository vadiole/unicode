package vadiole.unicode.ui.common

import android.content.Context
import android.os.Build
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class CollectionView(context: Context) : RecyclerView(context) {

    var isScrollEnabled = true

    init {
        overScrollMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            OVER_SCROLL_ALWAYS
        } else {
            OVER_SCROLL_NEVER
        }
        clipToPadding = false
        clipChildren = false
        itemAnimator = null
        super.setHasFixedSize(true)
    }

    abstract class Adapter : RecyclerView.Adapter<Cell>()

    class Cell(cell: View) : ViewHolder(cell)

    override fun canScrollVertically(direction: Int): Boolean {
        return isScrollEnabled && super.canScrollVertically(direction)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        return isScrollEnabled && super.canScrollHorizontally(direction)
    }
}
