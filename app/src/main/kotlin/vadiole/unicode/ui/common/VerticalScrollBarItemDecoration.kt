package vadiole.unicode.ui.common

import android.graphics.Canvas
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

class VerticalScrollBarItemDecoration(
    private val recyclerView: RecyclerView,
    private val scrollbarDrawable: ScrollbarDrawable,
    private val scrollBarWidth: Int,
) : RecyclerView.ItemDecoration() {

    enum class State(val alpha: Float) {
        HIDDEN(0f),
        VISIBLE(1f),
    }

    private var recyclerViewWidth = 0
    private var recyclerViewHeight = 0
    private val stateAnimator = SpringAnimator(initialValue = 0f, stiffness = SpringForce.STIFFNESS_VERY_LOW)
        .onUpdate { value ->
            scrollbarDrawable.alpha = (value * 255).toInt()
            requestRedraw()
        }
    private val hideRunnable = Runnable { state = State.HIDDEN }
    private var state = State.HIDDEN
        set(value) {
            if (field == value) return
            field = value
            stateAnimator.setValue(value.alpha)
            requestRedraw()
            if (value == State.VISIBLE) {
                recyclerView.removeCallbacks(hideRunnable)
                recyclerView.postDelayed(hideRunnable, 1500)
            }
        }
    private val mOnScrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateScrollPosition()
        }
    }

    init {
        recyclerView.addOnScrollListener(mOnScrollListener)
    }

    private fun updateScrollPosition() {
        val range = recyclerView.computeVerticalScrollRange()
        val offset = recyclerView.computeVerticalScrollOffset()
        val extent = recyclerView.computeVerticalScrollExtent()

        scrollbarDrawable.setParameters(range, offset, extent)
        state = State.VISIBLE
    }

    private fun requestRedraw() {
        recyclerView.invalidate()
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (recyclerViewWidth != recyclerView.width
            || recyclerViewHeight != recyclerView.height
        ) {
            recyclerViewWidth = recyclerView.width
            recyclerViewHeight = recyclerView.height
            // This is due to the different events ordering when keyboard is opened or
            // retracted vs rotate. Hence to avoid corner cases we just disable the
            // scroller when size changed, and wait until the scroll position is recomputed
            // before showing it back.
            this.state = State.HIDDEN
            return
        }

        scrollbarDrawable.setBounds(
            recyclerViewWidth - scrollBarWidth,
            recyclerView.paddingTop,
            recyclerViewWidth,
            recyclerViewHeight - recyclerView.paddingBottom
        )
        scrollbarDrawable.draw(c)
    }
}
