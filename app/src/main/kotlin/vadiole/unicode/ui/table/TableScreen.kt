package vadiole.unicode.ui.table

import android.content.Context
import android.view.Gravity.TOP
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updateLayoutParams
import kotlinx.coroutines.launch
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.ui.theme.Theme
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.utils.extension.*

class TableScreen(
    context: Context,
    private val appTheme: AppTheme,
    private val helper: TableHelper,
    private val delegate: Delegate
) : Screen(context), ThemeDelegate {
    private var spanCount = 8
    private val charCellDelegate = object : CharRow.Delegate {
        override fun onClick(codePoint: CodePoint) = delegate.onItemClick(codePoint)
        override fun onLongClick(codePoint: CodePoint) {
            context.toClipboard("Unicode", codePoint.char)
            Toast.makeText(context, "${codePoint.char} copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    private val tableAdapter = object : TableAdapter() {
        override fun getItemCount(): Int = helper.totalChars / spanCount
        override fun getBlock(position: Int) = helper.getBlock(position)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            val charCell = CharRow(context, appTheme, spanCount, charCellDelegate)
            return CollectionView.Cell(charCell)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val cell = holder.itemView as CharRow
            val codePoints = helper.getChars(position, spanCount)
            cell.bind(codePoints)
        }
    }
    private val topBar: TopBar = TopBar(context, appTheme, "Unicode") {
        tableView.smoothScrollToPosition(0)
    }
    private val tableViewDelegate = object : TableView.Delegate {
        override fun onBlockChanged(name: String?) {
            topBar.setTitle(name ?: "Unicode")
        }
    }
    private val tableView = TableView(context, tableAdapter, spanCount = spanCount, tableViewDelegate)

    init {
        appTheme.observe(this)
        setOnApplyWindowInsetsListener(this) { _, insets ->
            topBar.setPadding(0, insets.statusBars.top, 0, 0)
            tableView.setPadding(8.dp(context), 0, 8.dp(context), insets.navigationBars.bottom)
            tableView.updateLayoutParams<MarginLayoutParams> {
                setMargins(0, insets.statusBars.top + 50.dp(context), 0, 0)
            }
            insets
        }
        addView(tableView, frameParams(matchParent, matchParent, marginTop = 50.dp(context)))
        addView(topBar, frameParams(matchParent, wrapContent, gravity = TOP))
        launch {
            helper.loadChars(fast = true)
            tableAdapter.notifyDataSetChanged()
            helper.loadChars(fast = false)
            tableAdapter.notifyDataSetChanged()
            helper.loadBlocks()
            tableAdapter.notifyDataSetChanged()
        }
    }

    override fun applyTheme(theme: Theme) = Unit

    interface Delegate {
        fun onItemClick(codePoint: CodePoint)
    }
}