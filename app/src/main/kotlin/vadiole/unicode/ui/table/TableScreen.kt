package vadiole.unicode.ui.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.Gravity.LEFT
import android.view.Gravity.TOP
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.data.Block
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.binarySearch
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SearchBar
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.common.animatorsEnabled
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.ime
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.font_regular
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.hideKeyboard
import vadiole.unicode.ui.table.dock.DockController
import vadiole.unicode.ui.table.dock.DockView
import vadiole.unicode.ui.table.dock.FlyOverlay
import vadiole.unicode.ui.table.search.SearchController
import vadiole.unicode.ui.table.search.SearchHighlighter
import vadiole.unicode.ui.table.search.SearchHeaderCell
import vadiole.unicode.ui.table.search.SearchResultCell
import vadiole.unicode.ui.table.search.SearchResultView
import vadiole.unicode.ui.table.selector.BlockSelectorPopup
import vadiole.unicode.ui.table.selector.BlockSelectorView

class TableScreen(
    context: Context,
    private val tableController: TableController,
    private val searchController: SearchController,
    private val dockController: DockController,
    private val delegate: Delegate,
) : Screen(context) {
    private var spanCount = 8
    private var characterScale = userConfig.tableCharacterScale
    private var enlargeSmallChars = userConfig.enlargeSmallChars
    private var topInset = 0
    private var navigationInset = 0
    private var imeInset = 0
    private val statusBarPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val addHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    private val blockSelectorDelegate = object : BlockSelectorView.Delegate {
        override fun onBlockSelected(block: Block) {
            popup?.dismiss()
            tableView.scrollToPositionTop((tableController.getPosition(block) / spanCount) + 1)
        }
    }
    private val charCellDelegate = object : CharRow.Delegate {
        override fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float) {
            if (isDockEnabled) flyToDock(codePoint, source, centerX, centerY, glyphSize) else delegate.onShowDetails(codePoint)
        }

        override fun onLongClick(codePoint: CodePoint) {
            if (isDockEnabled) delegate.onShowDetails(codePoint) else copyText(codePoint.char)
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
            cell.characterScale = characterScale
            cell.enlargeSmallChars = enlargeSmallChars
            cell.bind(codePoints, abbreviations)
        }

        override fun onViewRecycled(holder: CollectionView.Cell) {
            (holder.itemView as CharRow).releaseSprites()
        }
    }
    private val searchResultCellDelegate = object : SearchResultCell.Delegate {
        override fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float) {
            if (isDockEnabled) {
                flyToDock(codePoint, source, centerX, centerY, glyphSize)
            } else {
                hideKeyboard()
                delegate.onShowDetails(codePoint)
            }
        }

        override fun onLongClick(codePoint: CodePoint) {
            if (isDockEnabled) {
                hideKeyboard()
                delegate.onShowDetails(codePoint)
            } else {
                copyText(codePoint.char)
            }
        }
    }
    private var searchJob: Job? = null
    private var isShowingRecents = false
    private val areRecentsDisplayed: Boolean
        get() = isShowingRecents && searchController.recentResult.isNotEmpty()
    private val onMoreResults: () -> Unit = { updateSearchResults() }
    private val searchAdapter = object : CollectionView.Adapter() {
        private val headerCount: Int
            get() = if (searchAtTop && areRecentsDisplayed) 1 else 0

        override fun getItemViewType(position: Int): Int {
            return if (headerCount > 0 && position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_RESULT
        }

        override fun getItemCount(): Int {
            return (if (isShowingRecents) searchController.recentResult.size else searchController.resultCount) + headerCount
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            val view = if (viewType == VIEW_TYPE_HEADER) {
                SearchHeaderCell(context)
            } else {
                SearchResultCell(context, searchResultCellDelegate)
            }
            return CollectionView.Cell(view)
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            if (holder.itemViewType == VIEW_TYPE_HEADER) return
            val view = holder.itemView as SearchResultCell
            val index = position - headerCount
            if (isShowingRecents) {
                view.bind(searchController.recentResult[index], tableController.abbreviations, null)
            } else {
                val result = searchController.result(index)
                view.bind(result, tableController.abbreviations, searchController.queryTokens)
                bindMatch(view, result, index)
                searchController.loadMore(index, onMoreResults)
            }
        }
    }

    private fun bindMatch(view: SearchResultCell, result: SearchResult, index: Int) {
        if (!searchController.hasMatch(index)) return
        val match = searchController.match(index, matchHighlighter)
        if (match != null) {
            view.bindMatch(match.ifEmpty { null })
            return
        }
        launch {
            val loaded = searchController.loadMatch(index, matchHighlighter)
            if (view.result === result && loaded.isNotEmpty()) view.bindMatch(loaded)
        }
    }

    private val matchHighlighter = SearchHighlighter(context.getColor(R.color.searchMatchHighlight))

    private var popup: BlockSelectorPopup? = null

    private val rootContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val contentContainer = FrameLayout(context)
    private val tableLayer = FrameLayout(context)
    private val tableDim = View(context).apply {
        setBackgroundColor(context.getColor(R.color.dialogDim))
        alpha = 0f
        visibility = GONE
    }
    private val resultsLayer = FrameLayout(context).apply {
        setBackgroundColor(context.getColor(R.color.layer0))
        visibility = INVISIBLE
        alpha = 0f
    }

    private val topBar: TopBar = TopBar(
        context = context,
        title = context.getString(R.string.app_name),
        centerTitle = userConfig.centerTitle,
        onTitleClick = { showBlockSelector() },
        onSettingsClick = { delegate.onSettingsClick() },
    )
    private val searchDelegate = object : SearchBar.Delegate {
        override fun onExpanded() = showSearch()
        override fun onCollapsed() = hideSearchInternal()
        override fun onTextChanged(query: String) = runSearch(query)
        override fun onAnimationRunning(progress: Float) = applySearchProgress(progress)
    }
    private val topBarHeight = 48.dp(context)
    private val searchBarHeight = SearchBar.barHeight(context)
    private var searchAtTop = userConfig.searchAtTop
    private var searchBar = SearchBar(context, searchDelegate, atTop = searchAtTop)
    private val dockDelegate: DockView.Delegate = object : DockView.Delegate {
        override fun onBackspace() {
            flyOverlay.cancelAll()
            if (dockController.backspace()) {
                dockView.setText(dockController.text)
                updateDockTip(animate = true)
            }
        }

        override fun onClear() {
            flyOverlay.cancelAll()
            if (dockController.clear()) {
                dockView.setText(dockController.text)
                updateDockTip(animate = true)
            }
        }

        override fun onCopyAll() {
            val text = dockController.text
            if (text.isEmpty()) return
            copyText(text)
            dockController.markCopied()
            updateDockTip(animate = true)
        }

        override fun onRestore(): Boolean {
            if (!dockController.restore()) return false
            dockView.setText(dockController.text, popFrom = 0)
            updateDockTip(animate = true)
            return true
        }

        override fun onTipCollapse(offset: Float) {
            tipCollapseOffset = offset
            applyKeyboardLift(keyboardLiftIme)
        }

        override fun onTipCollapsed() {
            tipCollapseOffset = 0f
            applyKeyboardLift(keyboardLiftIme)
            updateContentInsets()
        }
    }
    private val dockView: DockView = DockView(context, dockDelegate)
    private var isDockEnabled = true
    private var tipCollapseOffset = 0f
    private val flyOverlayDelegate = object : FlyOverlay.Delegate {
        override fun getSlotCenter(slotLeft: Float, slotRight: Float, out: FloatArray) =
            dockView.getSlotCenter(slotLeft, slotRight, out)

        override fun onLanded(end: Int) {
            dockView.reveal(end)
            val now = SystemClock.uptimeMillis()
            if (now - lastLandingHaptic >= landingHapticInterval) {
                lastLandingHaptic = now
                performHapticFeedback(addHaptic)
            }
        }
    }
    private val flyOverlay = FlyOverlay(context, flyOverlayDelegate)
    private val landingHapticInterval = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 17L else 30L
    private var lastLandingHaptic = 0L
    private var keyboardLiftIme = 0
    private val bottomContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private var fastScrollView: FastScrollView
    private var searchFastScrollView: FastScrollView
    private val tableViewDelegate = object : TableView.Delegate {
        override fun onBlockChanged(name: String?) {
            topBar.setTitle(name ?: context.getString(R.string.app_name))
        }

        override fun onScrollProgressChanged(progress: Float) {
            fastScrollView.setScrollProgress(tableController.scrollScale?.toThumb(progress) ?: progress)
        }
    }
    private val tableView: TableView = TableView(context, tableAdapter, spanCount = spanCount, tableViewDelegate).apply {
        addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                (view as CharRow).characterScale = characterScale
                view.enlargeSmallChars = enlargeSmallChars
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
    }
    private val fastScrollDelegate = object : FastScrollView.Delegate {
        override fun onFastScrollStart() = Unit

        override fun onFastScroll(progress: Float, precisionLevel: Int) {
            tableView.scrollToProgress(linearProgress(progress), precisionLevel)
        }

        override fun onFastScrollEnd() = Unit

        override fun getBlockName(progress: Float): String? {
            val itemCount = tableAdapter.itemCount
            if (itemCount <= 0) return null
            val position = (itemCount * linearProgress(progress)).toInt().coerceIn(0, itemCount - 1)
            return tableController.getBlock(position * spanCount)?.name
        }

        override fun precisionProgress(anchor: Float, delta: Float) = tableController.scrollScale?.offset(anchor, delta) ?: (anchor + delta)
    }
    private fun linearProgress(progress: Float) = tableController.scrollScale?.toLinear(progress) ?: progress

    private suspend fun loadBlocks() {
        tableController.loadBlocks()
        tableViewDelegate.onScrollProgressChanged(tableView.getScrollProgress())
    }

    private val searchFastScrollDelegate = object : FastScrollView.Delegate {
        override fun onFastScrollStart() {
            searchResultView.stopScroll()
            hideKeyboard()
        }

        override fun onFastScroll(progress: Float, precisionLevel: Int) {
            searchResultView.scrollToProgress(progress)
        }

        override fun onFastScrollEnd() = Unit

        override fun getBlockName(progress: Float): String? = null
    }
    private val searchResultViewDelegate: SearchResultView.Delegate = object : SearchResultView.Delegate {
        override fun onStartScrolling() {
            hideKeyboard()
        }

        override fun onScrollProgressChanged(progress: Float, canScroll: Boolean) {
            searchFastScrollView.setScrollProgress(progress)
            searchFastScrollView.visibility = if (canScroll) VISIBLE else INVISIBLE
        }
    }
    private val searchResultView = SearchResultView(context, searchAdapter, searchResultViewDelegate)
    private val noResultsView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        typeface = font_regular
        setTextColor(this@TableScreen.context.getColor(R.color.windowTextSecondary))
        gravity = Gravity.CENTER
        text = context.getString(R.string.search_no_results)
        visibility = GONE
    }
    private val fastScrollGestureRouter = FastScrollGestureRouter(this)
    private var isImeAnimationRunning = false

    init {
        fastScrollView = FastScrollView(context, fastScrollDelegate)
        searchFastScrollView = FastScrollView(context, searchFastScrollDelegate).apply {
            visibility = INVISIBLE
        }
        clipChildren = false
        clipToPadding = true
        isFocusableInTouchMode = true
        statusBarPaint.color = context.getColor(R.color.layer1)
        setWillNotDraw(false)
        setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBarInset = insets.statusBars.top
            if (paddingTop != statusBarInset) setPadding(0, statusBarInset, 0, 0)
            topInset = statusBarInset
            navigationInset = insets.navigationBars.bottom
            imeInset = insets.ime.bottom
            updateContentInsets()
            if (!isImeAnimationRunning) applyKeyboardLift(imeInset)
            insets
        }
        setWindowInsetsAnimationCallback(
            this,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        isImeAnimationRunning = true
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (isImeAnimationRunning) applyKeyboardLift(insets.ime.bottom)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        isImeAnimationRunning = false
                        applyKeyboardLift(imeInset)
                    }
                }
            }
        )
        addView(rootContainer, frameParams(matchParent, matchParent))
        rootContainer.addView(topBar, linearParams(matchParent, topBarHeight))
        rootContainer.addView(contentContainer, linearParams(matchParent, 0, weight = 1f))
        contentContainer.addView(tableLayer, frameParams(matchParent, matchParent))
        contentContainer.addView(resultsLayer, frameParams(matchParent, matchParent))
        tableLayer.addView(tableView, frameParams(matchParent, matchParent))
        tableLayer.addView(fastScrollView, frameParams(matchParent, matchParent))
        tableLayer.addView(tableDim, frameParams(matchParent, matchParent))
        resultsLayer.addView(searchResultView, frameParams(matchParent, matchParent))
        resultsLayer.addView(noResultsView, frameParams(matchParent, matchParent))
        resultsLayer.addView(searchFastScrollView, frameParams(matchParent, matchParent))
        addView(bottomContainer, frameParams(matchParent, wrapContent, gravity = Gravity.BOTTOM))
        bottomContainer.addView(dockView, linearParams(matchParent, wrapContent))
        attachSearchBar()
        addView(flyOverlay, frameParams(matchParent, matchParent))
        dockView.setText(dockController.text)
        dockView.scrollPosition = userConfig.dockScroll
        updateDockTip(animate = false)
        updateContentInsets()
        launch {
            val scrollCodePoint = userConfig.tableScrollCodePoint
            val isCached = tableController.loadCachedChars()
            if (!isCached && scrollCodePoint <= 0) {
                tableController.loadChars(fast = true)
            }
            tableController.loadAbbreviations()
            tableAdapter.notifyDataSetChanged()
            if (isCached) {
                scrollToCodePoint(scrollCodePoint, userConfig.tableScrollOffset)
                loadBlocks()
                if (tableController.needsRevalidation()) reloadChars(background = true)
            } else {
                tableController.loadChars(fast = false)
                tableAdapter.notifyDataSetChanged()
                scrollToCodePoint(scrollCodePoint, userConfig.tableScrollOffset)
                loadBlocks()
            }
        }
    }

    private fun firstVisibleCodePoint(chars: CodePointArray = tableController.tableChars): Int {
        val row = tableView.firstVisibleRow
        val index = row * spanCount
        return if (row > 0 && index < chars.size) chars[index].value else -1
    }

    private fun scrollToCodePoint(codePoint: Int, offset: Int) {
        if (codePoint <= 0) return
        val index = tableController.tableChars.binarySearch(CodePoint(codePoint))
        val position = if (index >= 0) index else -(index + 1)
        tableView.scrollToPositionTop(position / spanCount, offset)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val scroller = if (searchBar.isExpanded()) searchFastScrollView else fastScrollView
        return fastScrollGestureRouter.onInterceptTouchEvent(event, scroller)
    }

    fun setSearchAtTop(atTop: Boolean) {
        if (searchAtTop == atTop) return
        hideSearch()
        (searchBar.parent as ViewGroup).removeView(searchBar)
        searchAtTop = atTop
        searchBar = SearchBar(context, searchDelegate, atTop = atTop)
        attachSearchBar()
        applySearchProgress(0f)
        updateContentInsets()
    }

    private fun attachSearchBar() {
        if (searchAtTop) {
            contentContainer.addView(searchBar, frameParams(matchParent, searchBarHeight, gravity = TOP))
        } else {
            bottomContainer.addView(searchBar, 0, linearParams(matchParent, searchBarHeight))
        }
        contentContainer.updateLayoutParams<LinearLayout.LayoutParams> {
            bottomMargin = if (searchAtTop) -topBarHeight else 0
        }
    }

    fun setDockEnabled(enabled: Boolean) {
        if (isDockEnabled == enabled) return
        isDockEnabled = enabled
        flyOverlay.cancelAll()
        dockView.visibility = if (enabled) VISIBLE else GONE
        updateContentInsets()
    }

    fun saveState() {
        if (tableView.firstVisibleRow >= 0 && tableController.tableChars.isNotEmpty()) {
            userConfig.tableScrollCodePoint = firstVisibleCodePoint()
            userConfig.tableScrollOffset = tableView.firstVisibleRowOffset
        }
        userConfig.dockScroll = dockView.scrollPosition
    }

    private fun copyText(text: String) {
        context.toClipboard("Unicode", text)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val preview = if (text.length > 24) text.take(if (text[23].isHighSurrogate()) 23 else 24) + "…" else text
            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, preview), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateContentInsets() {
        val dockChrome = if (isDockEnabled) dockView.chromeHeight else 0
        val bottomPadding = if (isDockEnabled) 0 else navigationInset
        if (bottomContainer.paddingBottom != bottomPadding) bottomContainer.setPadding(0, 0, 0, bottomPadding)
        val dockHeight = dockChrome + navigationInset
        val chromeBottom = dockHeight + if (searchAtTop) 0 else searchBarHeight
        val tableBottom = chromeBottom + if (searchAtTop) topBarHeight else 0
        val contentTop = if (searchAtTop) searchBarHeight else 0
        val keyboardLift = (imeInset - navigationInset).coerceAtLeast(0)
        dockView.setBottomInset(navigationInset)
        if (tableView.paddingTop != contentTop || tableView.paddingBottom != tableBottom) {
            tableView.updatePadding(top = contentTop, bottom = tableBottom)
        }
        if (fastScrollView.paddingTop != contentTop || fastScrollView.paddingBottom != tableBottom) {
            fastScrollView.setPadding(0, contentTop, 0, tableBottom)
        }
        val resultsBottom = chromeBottom + keyboardLift
        searchResultView.keyboardInset = keyboardLift
        if (searchResultView.paddingTop != contentTop || searchResultView.paddingBottom != resultsBottom) {
            searchResultView.updatePadding(top = contentTop, bottom = resultsBottom)
        }
        if (noResultsView.paddingTop != contentTop || noResultsView.paddingBottom != resultsBottom) {
            noResultsView.setPadding(0, contentTop, 0, resultsBottom)
        }
        if (searchFastScrollView.paddingTop != contentTop || searchFastScrollView.paddingBottom != resultsBottom) {
            searchFastScrollView.setPadding(0, contentTop, 0, resultsBottom)
        }
    }

    private fun applyKeyboardLift(ime: Int) {
        keyboardLiftIme = ime
        bottomContainer.translationY = tipCollapseOffset - (ime - navigationInset).coerceAtLeast(0)
    }

    private fun updateDockTip(animate: Boolean) {
        val tip = if (!dockController.usedCopy && dockController.length > 0) DockView.Tip.Copy else DockView.Tip.Hidden
        val previousHeight = dockView.chromeHeight
        dockView.setTip(tip, animate)
        if (dockView.chromeHeight != previousHeight) updateContentInsets()
    }

    private fun applySearchProgress(p: Float) {
        applyTopSearchProgress(if (searchAtTop) p else 0f)
        val scaleProgress = if (searchAtTop) 0f else p
        val tableScale = 1f - 0.08f * scaleProgress
        tableLayer.scaleX = tableScale
        tableLayer.scaleY = tableScale
        tableDim.alpha = 0.2f * p
        tableDim.visibility = if (p > 0f) VISIBLE else GONE
        val resultsScale = if (searchAtTop) 1f else 1.08f - 0.08f * p
        resultsLayer.scaleX = resultsScale
        resultsLayer.scaleY = resultsScale
        resultsLayer.alpha = (p / 0.6f).coerceIn(0f, 1f)
        val isSettled = p <= 0f || p >= 1f
        val layerType = if (isSettled) LAYER_TYPE_NONE else LAYER_TYPE_HARDWARE
        if (resultsLayer.layerType != layerType) resultsLayer.setLayerType(layerType, null)
        if (tableLayer.layerType != layerType) tableLayer.setLayerType(layerType, null)
        tableLayer.visibility = if (p >= 1f) INVISIBLE else VISIBLE
        resultsLayer.visibility = if (p <= 0f) INVISIBLE else VISIBLE
    }

    private fun applyTopSearchProgress(p: Float) {
        val offset = -topBarHeight * p
        topBar.translationY = offset
        topBar.setContentAlpha(1f - p)
        topBar.visibility = if (p >= 1f) INVISIBLE else VISIBLE
        topBar.importantForAccessibility = if (searchAtTop && searchBar.isExpanded()) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        contentContainer.translationY = offset
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return fastScrollGestureRouter.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        searchController.cancel()
        fastScrollGestureRouter.cancel()
        super.onDetachedFromWindow()
    }

    private fun showBlockSelector() {
        val blockList = tableController.blockList
        if (blockList.blocks.isEmpty()) return

        val popup = popup ?: kotlin.run {
            val blockSelectorView = BlockSelectorView(context, blockList, blockSelectorDelegate)
            BlockSelectorPopup(blockSelectorView, wrapContent, wrapContent).also {
                it.setOnDismissListener { setMainScrollerHidden(false) }
                popup = it
            }
        }

        setMainScrollerHidden(true)
        if (popup.isShowing) {
            popup.reopen()
            return
        }
        tableView.stopScroll()
        popup.view.showCurrent(blockList.blocks.indexOf(tableView.currentBlock()))
        val xOffset = (topBar.width - popup.view.calculateWidth()) / 2
        val yOffset = (-8).dp(context)
        popup.setAnchorX(if (topBar.centerTitle) Float.NaN else 0f)
        popup.showAsDropDown(topBar, xOffset, yOffset, LEFT or TOP)
    }

    private fun setMainScrollerHidden(hidden: Boolean) {
        val alpha = if (hidden) 0f else 1f
        fastScrollView.animate().cancel()
        searchFastScrollView.animate().cancel()
        if (animatorsEnabled) {
            fastScrollView.animate().alpha(alpha).setDuration(150).start()
            searchFastScrollView.animate().alpha(alpha).setDuration(150).start()
        } else {
            fastScrollView.alpha = alpha
            searchFastScrollView.alpha = alpha
        }
    }

    fun addToDock(codePoint: CodePoint) {
        val start = dockController.length
        dockController.add(codePoint)
        dockView.setText(dockController.text, popFrom = start)
        updateDockTip(animate = true)
        performHapticFeedback(addHaptic)
        delegate.onItemAdded(codePoint)
    }

    private fun flyToDock(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float) {
        if (!animatorsEnabled) {
            addToDock(codePoint)
            return
        }
        val start = dockController.length
        val slotLeft = dockView.textWidth
        dockController.add(codePoint)
        dockView.setText(dockController.text, hiddenFrom = start)
        val slotRight = dockView.textWidth
        val abbreviation = tableController.abbreviations[codePoint]
        flyOverlay.launch(
            label = abbreviation ?: codePoint.char,
            isAbbreviation = abbreviation != null,
            source = source,
            centerX = centerX,
            centerY = centerY,
            glyphSize = glyphSize,
            slotLeft = slotLeft,
            slotRight = slotRight,
            end = dockController.length,
        )
        updateDockTip(animate = true)
        performHapticFeedback(addHaptic)
        delegate.onItemAdded(codePoint)
    }

    fun reloadChars(background: Boolean = false) {
        launch {
            if (background) tableController.awaitSearch()
            val chars = tableController.tableChars
            if (!tableController.loadChars(fast = false, background)) return@launch
            val topCodePoint = firstVisibleCodePoint(chars)
            val topOffset = tableView.firstVisibleRowOffset
            tableAdapter.notifyDataSetChanged()
            scrollToCodePoint(topCodePoint, topOffset)
            loadBlocks()
            popup = null
        }
    }

    fun isSearchVisible(): Boolean = searchBar.isExpanded()

    fun startSearchBack() = searchBar.startScrub()

    fun progressSearchBack(progress: Float) = searchBar.scrub(1f - 0.15f * progress)

    fun cancelSearchBack() = searchBar.cancelScrub()

    fun hideSearch(): Boolean {
        if (!searchBar.isExpanded()) return false
        searchBar.collapse()
        return true
    }

    private fun updateSearchResults() {
        topBar.setSearchRecents(!searchAtTop && areRecentsDisplayed)
        searchAdapter.notifyDataSetChanged()
        noResultsView.visibility = if (!isShowingRecents && searchController.resultCount == 0) VISIBLE else GONE
    }

    private fun showSearch() {
        noResultsView.visibility = GONE
        isShowingRecents = true
        topBar.setSearchShown(!searchAtTop)
        searchController.recentResult = emptyArray()
        searchController.cancel()
        updateSearchResults()
        searchResultView.scrollToPosition(0)
        delegate.onSearchShown()
        searchJob?.cancel()
        searchJob = launch {
            searchController.loadRecents()
            updateSearchResults()
        }
    }

    private fun hideSearchInternal() {
        topBar.setSearchShown(false)
        searchJob?.cancel()
        searchController.cancel()
        searchResultView.stopScroll()
        delegate.onSearchHidden()
    }

    fun setCharacterScale(value: Float) {
        characterScale = value
        forEachRow { it.characterScale = value }
        tableView.onCharacterScaleChanged()
    }

    fun setCharacterScaleAdjusting(adjusting: Boolean) {
        tableView.setScaleAdjusting(adjusting)
    }

    fun setEnlargeSmallChars(enabled: Boolean) {
        enlargeSmallChars = enabled
        forEachRow { it.enlargeSmallChars = enabled }
    }

    private inline fun forEachRow(action: (CharRow) -> Unit) {
        for (index in 0 until tableView.childCount) {
            action(tableView.getChildAt(index) as CharRow)
        }
    }

    fun refreshRecents() {
        if (isShowingRecents) runSearch("")
    }

    private fun runSearch(query: String) {
        if (!searchBar.isExpanded()) return
        searchJob?.cancel()
        if (query.isEmpty()) {
            searchController.cancel()
            searchJob = launch {
                isShowingRecents = true
                noResultsView.visibility = GONE
                searchResultView.stopScroll()
                searchController.loadRecents()
                searchResultView.scrollToPosition(0)
                updateSearchResults()
            }
        } else {
            searchController.search(query) {
                isShowingRecents = false
                searchResultView.stopScroll()
                searchResultView.scrollToPosition(0)
                updateSearchResults()
            }
        }
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

    private companion object {
        const val VIEW_TYPE_RESULT = 0
        const val VIEW_TYPE_HEADER = 1
    }

    interface Delegate {
        fun onShowDetails(codePoint: CodePoint)

        fun onItemAdded(codePoint: CodePoint)

        fun onSearchShown()

        fun onSearchHidden()

        fun onSettingsClick()
    }
}
