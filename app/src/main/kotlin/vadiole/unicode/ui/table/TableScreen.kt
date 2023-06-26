package vadiole.unicode.ui.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity.TOP
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updatePadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SearchBar
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.table.search.SearchHelper
import vadiole.unicode.ui.table.search.SearchResultCell
import vadiole.unicode.ui.table.search.SearchResultView
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_topBarBackground
import vadiole.unicode.ui.theme.key_windowDivider
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.hideKeyboard
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.navigationBars
import vadiole.unicode.utils.extension.statusBars
import vadiole.unicode.utils.extension.toClipboard

class TableScreen(
    context: Context,
    private val tableHelper: TableHelper,
    private val searchHelper: SearchHelper,
    private val delegate: Delegate,
) : Screen(context), ThemeDelegate {
    private var spanCount = 8
    private var topInset = 0
    private val statusBarPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val charCellDelegate = object : CharRow.Delegate {
        override fun onClick(codePoint: CodePoint) = delegate.onItemClick(codePoint)
        override fun onLongClick(codePoint: CodePoint) {
            context.toClipboard("Unicode", codePoint.char)
            Toast.makeText(context, "${codePoint.char} copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    private val tableAdapter = object : TableAdapter() {
        override fun getItemCount(): Int = tableHelper.totalChars / spanCount
        override fun getBlock(position: Int) = tableHelper.getBlock(position)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            val charCell = CharRow(context, spanCount, charCellDelegate)
            return CollectionView.Cell(charCell)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val cell = holder.itemView as CharRow
            val codePoints = tableHelper.getChars(position, spanCount)
            cell.bind(codePoints)
        }
    }
    private val searchResultCellDelegate = object : SearchResultCell.Delegate {
        override fun onClick(codePoint: CodePoint) {
            hideKeyboard()
            delegate.onItemClick(codePoint)
        }
    }
    private val searchAdapter = object : CollectionView.Adapter() {
        override fun getItemCount(): Int = searchHelper.searchResult.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            return CollectionView.Cell(SearchResultCell(context, searchResultCellDelegate))
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val view = holder.itemView as SearchResultCell
            val data = searchHelper.searchResult[position]
            view.bind(data)
        }
    }
    private val topBar: TopBar = TopBar(context, "Unicode") {
        tableView.smoothScrollToPosition(0)
    }
    private val searchDelegate = object : SearchBar.Delegate {
        override fun onFocused(): Boolean {
            tableView.visibility = GONE
            searchResultView.visibility = VISIBLE
            return true
        }

        override fun onUnfocused() = hideSearch()

        private var searchJob: Job? = null
        override fun onTextChanged(string: String) {
            searchJob?.cancel()
            searchJob = launch {
                searchHelper.search(string)
                searchResultView.scrollToPosition(0)
                searchAdapter.notifyDataSetChanged()
            }
        }

        override fun onAnimationRunning(progress: Float) {
            translationY = -topBar.measuredHeight * progress
            topBar.titleView.alpha = 1 - progress
        }
    }
    private val searchBar = SearchBar(context, searchDelegate)
    private val divider = View(context)
    private val tableViewDelegate = object : TableView.Delegate {
        override fun onBlockChanged(name: String?) {
            topBar.setTitle(name ?: "Unicode")
        }
    }
    private val tableView = TableView(context, tableAdapter, spanCount = spanCount, tableViewDelegate)
    private val searchResultViewDelegate: SearchResultView.Delegate = object : SearchResultView.Delegate {
        override fun onStartScrolling() {
            hideKeyboard()
        }
    }
    private val searchResultView = SearchResultView(context, searchAdapter, searchResultViewDelegate).apply {
        visibility = GONE
    }

    init {
        themeManager.observe(this)
        setWillNotDraw(false)
        setOnApplyWindowInsetsListener(this) { _, insets ->
            setPadding(0, insets.statusBars.top, 0, 0)
            tableView.updatePadding(
                bottom = insets.navigationBars.bottom
            )
            topInset = insets.statusBars.top
            insets
        }
        addView(topBar, frameParams(matchParent, 42.dp(context), gravity = TOP))
        addView(searchBar, frameParams(matchParent, 50.dp(context), marginTop = 42.dp(context)))
        addView(divider, frameParams(matchParent, 1, marginTop = 92.dp(context)))
        addView(tableView, frameParams(matchParent, matchParent, marginTop = 92.dp(context)))
        addView(searchResultView, frameParams(matchParent, matchParent, marginTop = 92.dp(context)))
        launch {
            tableHelper.loadChars(fast = true)
            tableAdapter.notifyDataSetChanged()
            tableHelper.loadChars(fast = false)
            tableAdapter.notifyDataSetChanged()
            tableHelper.loadBlocks()
            tableAdapter.notifyDataSetChanged()
        }
    }

    fun hideSearch(): Boolean {
        if (searchResultView.visibility != GONE) {
            searchBar.searchView.clearFocus()
            tableView.visibility = VISIBLE
            searchResultView.visibility = GONE
            return true
        }
        return false
    }

    fun scrollToChar(codePoint: Int) {
        val position = tableHelper.tableChars.binarySearch { codePointToCompare ->
            when {
                codePointToCompare.value > codePoint -> 1
                codePointToCompare.value < codePoint -> -1
                else -> 0
            }
        }
        val row = position / spanCount
        val indexInRow = position % spanCount
        if (position >= 0) {
            tableView.scrollToPositionInCenter(row, indexInRow)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f,measuredWidth.toFloat(), topInset.toFloat(), statusBarPaint)
    }

    override fun applyTheme() {
        divider.setBackgroundColor(themeManager.getColor(key_windowDivider))
        statusBarPaint.color = themeManager.getColor(key_topBarBackground)
    }

    interface Delegate {
        fun onItemClick(codePoint: CodePoint)
    }
}