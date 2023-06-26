package vadiole.unicode.ui.table.selector

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.data.Block
import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.utils.extension.dp

class BlockSelectorView(
    context: Context,
    private val blocks: List<Block>,
    private val delegate: Delegate,
) : CollectionView(context) {
    interface Delegate {
        fun onBlockSelected(block: Block)
    }

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

    private val itemDecoration = CollectionItemDecoration(leftPadding = 14f.dp(context))

    init {
        recycledViewPool.setMaxRecycledViews(0, 32)
        layoutManager = blockSelectorLayoutManager
        setAdapter(adapter)
        setItemViewCacheSize(8)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
    }
}
