package vadiole.unicode.ui.table

import android.content.Context
import android.view.Gravity.TOP
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updateLayoutParams
import kotlinx.coroutines.launch
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
    private val controller: TableController,
    private val delegate: Delegate
) : Screen(context), ThemeDelegate {
    private val charCellDelegate = object : CharCell.Delegate {
        override fun onClick(codePoint: Int) = delegate.onItemClick(codePoint)
        override fun onLongClick(codePoint: Int) {
            val char = String(Character.toChars(codePoint))
            context.toClipboard("Unicode", char)
            Toast.makeText(context, "$char copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    private val tableAdapter = object : CollectionView.Adapter() {
        override fun getItemCount(): Int = controller.totalChars
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            val charCell = CharCell(context, appTheme, charCellDelegate)
            return CollectionView.Cell(charCell)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val cell = holder.itemView as CharCell
            val codePoint = controller.tableCodePoints.getOrNull(position) ?: 0
            val char = String(Character.toChars(codePoint))
            cell.bind(codePoint, char)
        }
    }
    private val topBar = TopBar(context, appTheme, "Unicode") {
        tableView.smoothScrollToPosition(0)
    }
    private val tableView = TableView(context, tableAdapter, spanCount = 8)

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
            controller.loadChars()
            tableAdapter.notifyDataSetChanged()
        }
    }

    override fun applyTheme(theme: Theme) = Unit

    interface Delegate {
        fun onItemClick(codePoint: Int)
    }
}