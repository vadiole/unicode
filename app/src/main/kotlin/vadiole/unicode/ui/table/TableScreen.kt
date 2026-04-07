package vadiole.unicode.ui.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.Gravity.LEFT
import android.view.Gravity.TOP
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updatePadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vadiole.unicode.R
import vadiole.unicode.data.Block
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.binarySearch
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SearchBar
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.roboto_regular
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.hideKeyboard
import vadiole.unicode.ui.table.search.SearchController
import vadiole.unicode.ui.table.search.SearchHeaderCell
import vadiole.unicode.ui.table.search.SearchResultCell
import vadiole.unicode.ui.table.search.SearchResultView
import vadiole.unicode.ui.table.selector.BlockSelectorPopup
import vadiole.unicode.ui.table.selector.BlockSelectorView

class TableScreen(
    context: Context,
    private val tableController: TableController,
    private val searchController: SearchController,
    private val delegate: Delegate,
) : Screen(context) {
    private var spanCount = 8
    private var topInset = 0
    private val statusBarPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val blockSelectorDelegate = object : BlockSelectorView.Delegate {
        override fun onBlockSelected(block: Block) {
            popup?.dismiss()
            tableView.scrollToPositionTop((tableController.getPosition(block) / spanCount) + 1)
        }
    }
    private val charCellDelegate = object : CharRow.Delegate {
        override fun onClick(codePoint: CodePoint) = delegate.onItemClick(codePoint)
        override fun onLongClick(codePoint: CodePoint) {
            context.toClipboard("Unicode", codePoint.char)
            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, codePoint.char), Toast.LENGTH_SHORT).show()
        }
    }
    private val tableAdapter = object : TableAdapter() {
        override fun getItemCount(): Int = tableController.totalChars / spanCount
        override fun getBlock(position: Int) = tableController.getBlock(position)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            val charCell = CharRow(context, spanCount, charCellDelegate)
            return CollectionView.Cell(charCell)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val cell = holder.itemView as CharRow
            val codePoints = tableController.getChars(position, spanCount)
            val abbreviations = tableController.abbreviations
            cell.bind(codePoints, abbreviations)
        }
    }
    private val searchResultCellDelegate = object : SearchResultCell.Delegate {
        override fun onClick(codePoint: CodePoint) {
            hideKeyboard()
            delegate.onItemClick(codePoint)
        }
    }
    private var searchJob: Job? = null
    private var isShowingRecents = false
    private val searchAdapter = object : CollectionView.Adapter() {
        override fun getItemViewType(position: Int): Int {
            if (isShowingRecents && position == 0) return VIEW_TYPE_HEADER
            return VIEW_TYPE_RESULT
        }

        override fun getItemCount(): Int {
            return if (isShowingRecents) {
                val count = searchController.recentResult.size
                if (count > 0) count + 1 else 0
            } else {
                searchController.searchResult.size
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            return when (viewType) {
                VIEW_TYPE_HEADER -> CollectionView.Cell(SearchHeaderCell(context))
                else -> CollectionView.Cell(SearchResultCell(context, searchResultCellDelegate))
            }
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            if (holder.itemViewType == VIEW_TYPE_HEADER) return
            val view = holder.itemView as SearchResultCell
            val data = if (isShowingRecents) {
                searchController.recentResult[position - 1]
            } else {
                searchController.searchResult[position]
            }
            view.bind(data, tableController.abbreviations)
        }
    }

    private var popup: BlockSelectorPopup? = null

    private val topBar: TopBar = TopBar(context, context.getString(R.string.app_name)) {
        if (tableController.blocks.isEmpty()) return@TopBar

        val popup = popup ?: kotlin.run {
            val blockSelectorView = BlockSelectorView(context, tableController.blocks, blockSelectorDelegate)
            BlockSelectorPopup(blockSelectorView, wrapContent, wrapContent).also {
                popup = it
            }
        }

        val xOffset = (width - popup.view.calculateWidth()) / 2
        val yOffset = (-8).dp(context)
        popup.showAsDropDown(this, xOffset, yOffset, LEFT or TOP)
    }
    private val searchDelegate = object : SearchBar.Delegate {
        override fun onFocused(): Boolean {
            tableView.visibility = GONE
            searchResultView.visibility = VISIBLE
            noResultsView.visibility = GONE
            isShowingRecents = true
            searchController.recentResult = emptyArray()
            searchAdapter.notifyDataSetChanged()
            delegate.onSearchFocused()
            searchJob?.cancel()
            searchJob = launch {
                searchController.loadRecents()
                searchAdapter.notifyDataSetChanged()
            }
            return true
        }

        override fun onUnfocused(): Boolean {
            delegate.onSearchUnfocused()
            return hideSearch()
        }

        override fun onTextChanged(string: String) {
            searchJob?.cancel()
            searchJob = launch {
                if (string.isEmpty()) {
                    isShowingRecents = true
                    noResultsView.visibility = GONE
                    searchResultView.stopScroll()
                    searchController.loadRecents()
                    searchResultView.scrollToPosition(0)
                    searchAdapter.notifyDataSetChanged()
                } else {
                    isShowingRecents = false
                    searchResultView.stopScroll()
                    searchController.search(string, 64)
                    searchResultView.scrollToPosition(0)
                    searchAdapter.notifyDataSetChanged()
                    noResultsView.visibility = if (searchController.searchResult.isEmpty()) VISIBLE else GONE
                    searchController.search(string)
                    searchAdapter.notifyDataSetChanged()
                    noResultsView.visibility = if (searchController.searchResult.isEmpty()) VISIBLE else GONE
                }
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
            topBar.setTitle(name ?: context.getString(R.string.app_name))
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
    private val noResultsView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        typeface = roboto_regular
        setTextColor(this@TableScreen.context.getColor(R.color.windowTextSecondary))
        gravity = Gravity.CENTER
        text = context.getString(R.string.search_no_results)
        visibility = GONE
    }

    init {
        clipChildren = true
        clipToPadding = false
        divider.setBackgroundColor(context.getColor(R.color.windowDivider))
        statusBarPaint.color = context.getColor(R.color.topBarBackground)
        setWillNotDraw(false)
        setOnApplyWindowInsetsListener(this) { _, insets ->
            setPadding(0, insets.statusBars.top, 0, 0)
            tableView.updatePadding(
                bottom = insets.navigationBars.bottom
            )
            searchResultView.updatePadding(
                bottom = insets.navigationBars.bottom
            )
            topInset = insets.statusBars.top
            insets
        }
        addView(topBar, frameParams(matchParent, 42.dp(context), gravity = TOP))
        addView(searchBar, frameParams(matchParent, 50.dp(context), marginTop = 42.dp(context)))
        addView(divider, frameParams(matchParent, 1, marginTop = 92.dp(context)))
        addView(tableView, frameParams(matchParent, matchParent, marginTop = 92.dp(context)))
        addView(searchResultView, frameParams(matchParent, matchParent, marginTop = 92.dp(context), marginBottom = (-42).dp(context)))
        addView(noResultsView, frameParams(matchParent, matchParent, marginTop = 92.dp(context), marginBottom = (-42).dp(context)))
        launch {
            tableController.loadChars(fast = true)
            tableController.loadAbbreviations()
            tableAdapter.notifyDataSetChanged()
            tableController.loadChars(fast = false)
            tableAdapter.notifyDataSetChanged()
            tableController.loadBlocks()
        }
    }

    fun isSearchVisible(): Boolean {
        return searchResultView.visibility == VISIBLE
    }

    fun hideSearch(): Boolean {
        if (searchResultView.visibility != GONE) {
            searchJob?.cancel()
            searchBar.searchView.clearFocus()
            tableView.visibility = VISIBLE
            searchResultView.visibility = GONE
            noResultsView.visibility = GONE
            return true
        }
        return false
    }

    fun scrollToChar(codePoint: CodePoint) {
        val position = tableController.tableChars.binarySearch(codePoint)
        val row = position / spanCount
        val indexInRow = position % spanCount
        if (position >= 0) {
            tableView.scrollToPositionInCenter(row, indexInRow)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, measuredWidth.toFloat(), topInset.toFloat(), statusBarPaint)
    }

    interface Delegate {
        fun onItemClick(codePoint: CodePoint)

        fun onSearchFocused()

        fun onSearchUnfocused()
    }

    companion object {
        private const val VIEW_TYPE_RESULT = 0
        private const val VIEW_TYPE_HEADER = 1
    }
}