package vadiole.unicode.ui.common

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class CollectionView(context: Context) : RecyclerView(context) {

    var isScrollEnabled = true
    var fastScrollAnimationRunning = false

    init {
        overScrollMode = View.OVER_SCROLL_ALWAYS
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
