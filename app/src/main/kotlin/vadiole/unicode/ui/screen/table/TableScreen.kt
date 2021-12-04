package vadiole.unicode.ui.screen.table

import android.content.Context
import android.util.Log
import android.view.Gravity.TOP
import android.view.ViewGroup
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updateLayoutParams
import kotlinx.coroutines.launch
import vadiole.unicode.ui.components.CollectionView
import vadiole.unicode.ui.components.Screen
import vadiole.unicode.ui.components.Toolbar
import vadiole.unicode.ui.components.dialogCharDetails
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.ui.theme.Theme
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.utils.*

class TableScreen(
    context: Context,
    private val appTheme: AppTheme,
    private val controller: TableController,
) : Screen(context), ThemeDelegate {
    private val charCellDelegate = object : CharCell.Delegate {
        override fun onClick(position: Int) {
            launch {
                val description = controller.getDescription(position)
                val codePoint = controller.getCodePoint(position)
                val char = controller.tableChars[position]
                val dialog = context.dialogCharDetails(appTheme, char, description, codePoint)
                dialog.show()
            }
        }

        override fun onLongClick(position: Int) {
            val char = controller.tableChars[position]
            context.toClipboard("Unicode", char)
            toast("$char copied to clipboard")
        }
    }

    private val tableAdapter = object : CollectionView.Adapter() {
        override fun getItemCount(): Int = controller.totalChars

        var total = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            Log.d("TABLE", "onCreateViewHolder: ${total++}")
            val charCell = CharCell(context, appTheme, charCellDelegate)
            return CollectionView.Cell(charCell)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            Log.d("TABLE", "onBindViewHolder: $position")
            val cell = holder.itemView as CharCell
            val char = controller.tableChars.getOrNull(position) ?: ""
            cell.bind(position, char)
        }
    }

    private val toolbar = Toolbar(context, appTheme, "Unicode") {
        tableView.smoothScrollToPosition(0)
    }
    private val tableView = TableView(context, tableAdapter, spanCount = 8)

    init {
        appTheme.observe(this)
        setOnApplyWindowInsetsListener(this) { _, insets ->
            toolbar.setPadding(0, insets.statusBars.top, 0, 0)
            tableView.setPadding(8.dp(context), 0, 8.dp(context), insets.navigationBars.bottom)
            tableView.updateLayoutParams<MarginLayoutParams> {
                setMargins(0, insets.statusBars.top + 50.dp(context), 0, 0)
            }
            insets
        }

        addView(tableView, frame(fill, fill, marginTop = 50.dp(context)))
        addView(toolbar, frame(fill, wrap, gravity = TOP))

        launch {
            controller.loadChars()
            tableAdapter.notifyDataSetChanged()
        }
    }

    override fun applyTheme(theme: Theme) = Unit
}