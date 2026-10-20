package vadiole.unicode.ui.table.selector

import android.content.Context
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LayoutAnimationController
import android.view.animation.PathInterpolator
import android.view.animation.ScaleAnimation
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.R
import vadiole.unicode.data.Block
import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.ScrollbarDrawable
import vadiole.unicode.ui.common.VerticalScrollBarItemDecoration
import vadiole.unicode.ui.common.dp

class BlockSelectorView(
    context: Context,
    private val blocks: Array<Block>,
    private val delegate: Delegate,
) : CollectionView(context) {
    interface Delegate {
        fun onBlockSelected(block: Block)
    }

    private val floatingInset = BlockSelectorGeometry.FLOATING_INSET_DP.dp(context)
    private val topMargin = (
        BlockSelectorGeometry.ARROW_HEIGHT_DP.dp(context) -
            BlockSelectorGeometry.ARROW_OVERLAP_DP.dp(context)
        ).toInt()
    private val blockSelectorLayoutManager = LinearLayoutManager(context).apply {
        orientation = LinearLayoutManager.VERTICAL
    }

    private val adapter = object : Adapter() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell {
            return Cell(BlockSelectorCell(context))
        }

        override fun onBindViewHolder(holder: Cell, position: Int) {
            val block = blocks[position]
            val cell = holder.itemView as BlockSelectorCell
            cell.text = block.name
            cell.setOnClickListener {
                delegate.onBlockSelected(block)
            }
        }

        override fun getItemCount(): Int {
            return blocks.size
        }
    }

    private val scrollbarDrawable = ScrollbarDrawable()
    private val scrollBars = VerticalScrollBarItemDecoration(
        recyclerView = this,
        scrollbarDrawable = scrollbarDrawable,
        scrollBarWidth = 4.dp(context),
    )
    private val divider = CollectionItemDecoration(leftPadding = 14f.dp(context))

    init {
        scrollbarDrawable.setColor(this.context.getColor(R.color.scrollIndicator))
        recycledViewPool.setMaxRecycledViews(0, 32)
        layoutManager = blockSelectorLayoutManager
        setItemViewCacheSize(8)
        setAdapter(adapter)
        addItemDecoration(divider)
        addItemDecoration(scrollBars)
        setPadding(
            floatingInset.toInt(),
            BlockSelectorGeometry.CONTENT_VERTICAL_PADDING_DP.dp(context) + topMargin + floatingInset.toInt(),
            floatingInset.toInt(),
            BlockSelectorGeometry.CONTENT_VERTICAL_PADDING_DP.dp(context) + floatingInset.toInt()
        )
        layoutAnimation = LayoutAnimationController(
            AnimationSet(true).apply {
                interpolator = showInterpolator
                addAnimation(
                    ScaleAnimation(
                        0.95f, 1f, 0.95f, 1f, Animation.RELATIVE_TO_SELF, 0.1f, Animation.RELATIVE_TO_SELF, 0.5f
                    ).apply {
                        duration = 300
                    }
                )
                addAnimation(
                    AlphaAnimation(
                        0.7f, 1f
                    ).apply {
                        duration = 300
                    }
                )
            },
        ).apply {
            order = LayoutAnimationController.ORDER_NORMAL
            delay = 0.04f
        }
        adapter.notifyDataSetChanged()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(calculateWidth(), MeasureSpec.EXACTLY)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(calculateHeight(), MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun calculateWidth(): Int {
        val displayWidth = context.resources.displayMetrics.widthPixels
        return 500.dp(context).coerceAtMost((displayWidth * .95f).toInt())
    }

    fun calculateHeight(): Int {
        val displayHeight = context.resources.displayMetrics.heightPixels
        return (700 - 64).dp(context).coerceAtMost((displayHeight * .75f).toInt())
    }

    companion object {
        private val showInterpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }
}
