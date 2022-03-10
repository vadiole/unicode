package vadiole.unicode.ui.table

import vadiole.unicode.data.Block
import vadiole.unicode.ui.common.CollectionView

abstract class TableAdapter : CollectionView.Adapter() {
    abstract fun getBlock(position: Int): Block?
}