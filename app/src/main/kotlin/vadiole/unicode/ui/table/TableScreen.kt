package vadiole.unicode.ui.table

import android.animation.ValueAnimator
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
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.data.Block
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.data.binarySearch
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SearchBar
import vadiole.unicode.ui.common.TopBar
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.ime
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.mono_regular
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.hideKeyboard
import vadiole.unicode.ui.table.dock.DockController
import vadiole.unicode.ui.table.dock.DockView
import vadiole.unicode.ui.table.dock.FlyOverlay
import vadiole.unicode.ui.table.search.SearchController
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
            cell.bind(codePoints, abbreviations)
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
    private val searchAdapter = object : CollectionView.Adapter() {
        override fun getItemCount(): Int {
            return if (isShowingRecents) searchController.recentResult.size else searchController.searchResult.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionView.Cell {
            return CollectionView.Cell(SearchResultCell(context, searchResultCellDelegate))
        }

        override fun onBindViewHolder(holder: CollectionView.Cell, position: Int) {
            val view = holder.itemView as SearchResultCell
            val data = if (isShowingRecents) {
                searchController.recentResult[position]
            } else {
                searchController.searchResult[position]
            }
            val tokens = if (isShowingRecents) null else searchController.queryTokens
            view.bind(data, tableController.abbreviations, tokens)
        }
    }

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
        onTitleClick = { showBlockSelector() },
        onSettingsClick = { delegate.onSettingsClick() },
    )
    private val searchDelegate = object : SearchBar.Delegate {
        override fun onExpanded() = showSearch()
        override fun onCollapsed() = hideSearchInternal()
        override fun onTextChanged(query: String) = runSearch(query)
        override fun onAnimationRunning(progress: Float) = applySearchProgress(progress)
    }
    private val searchBarHeight = SearchBar.barHeight(context)
    private val searchBar = SearchBar(context, searchDelegate)
    private val dockDelegate: DockView.Delegate = object : DockView.Delegate {
        override fun onBackspace() {
            flyOverlay.cancelAll()
            if (dockController.backspace()) dockView.setText(dockController.text)
        }

        override fun onClear() {
            flyOverlay.cancelAll()
            if (dockController.clear()) dockView.setText(dockController.text)
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
            fastScrollView.setScrollProgress(progress)
        }
    }
    private val tableView = TableView(context, tableAdapter, spanCount = spanCount, tableViewDelegate)
    private val fastScrollDelegate = object : FastScrollView.Delegate {
        override fun onFastScrollStart() = Unit

        override fun onFastScroll(progress: Float, precisionLevel: Int) {
            tableView.scrollToProgress(progress, precisionLevel)
        }

        override fun onFastScrollEnd() = Unit

        override fun getBlockName(progress: Float): String? {
            val itemCount = tableAdapter.itemCount
            if (itemCount <= 0) return null
            val position = (itemCount * progress).toInt().coerceIn(0, itemCount - 1)
            return tableController.getBlock(position * spanCount)?.name
        }
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
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        typeface = mono_regular
        setTextColor(this@TableScreen.context.getColor(R.color.windowTextSecondary))
        gravity = Gravity.CENTER
        text = context.getString(R.string.search_no_results)
        visibility = GONE
    }
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tmpLocation = IntArray(2)
    private var downRawX = 0f
    private var downRawY = 0f
    private var interceptedFastScrollView: FastScrollView? = null
    private var isImeAnimationRunning = false

    init {
        fastScrollView = FastScrollView(context, fastScrollDelegate)
        searchFastScrollView = FastScrollView(context, searchFastScrollDelegate).apply {
            visibility = INVISIBLE
        }
        clipChildren = false
        clipToPadding = false
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
        rootContainer.addView(topBar, linearParams(matchParent, 48.dp(context)))
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
        bottomContainer.addView(searchBar, linearParams(matchParent, searchBarHeight))
        bottomContainer.addView(dockView, linearParams(matchParent, wrapContent))
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
                tableController.loadBlocks()
                reloadChars()
            } else {
                tableController.loadChars(fast = false)
                tableAdapter.notifyDataSetChanged()
                scrollToCodePoint(scrollCodePoint, userConfig.tableScrollOffset)
                tableController.loadBlocks()
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
        val fastScrollView = if (searchBar.isExpanded()) searchFastScrollView else fastScrollView
        if (!fastScrollView.isShown) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                interceptedFastScrollView = null
            }

            MotionEvent.ACTION_MOVE -> {
                fastScrollView.getLocationOnScreen(tmpLocation)
                val localX = downRawX - tmpLocation[0]
                val localY = downRawY - tmpLocation[1]
                if (localY < fastScrollView.paddingTop || localY >= fastScrollView.height - fastScrollView.paddingBottom) return false
                if (!fastScrollView.isInThumbZone(localX, localY) &&
                    !fastScrollView.isInEdgeZone(localX)
                ) return false
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dx * dx + dy * dy > scaledTouchSlop * scaledTouchSlop) {
                    interceptedFastScrollView = fastScrollView
                    val down = MotionEvent.obtain(
                        event.downTime, event.eventTime,
                        MotionEvent.ACTION_DOWN,
                        fastScrollView.width.toFloat() - 1f,
                        localY,
                        0
                    )
                    fastScrollView.onTouchEvent(down)
                    down.recycle()
                    return true
                }
            }
        }
        return false
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
        val chromeBottom = dockHeight + searchBarHeight
        val keyboardLift = (imeInset - navigationInset).coerceAtLeast(0)
        dockView.setBottomInset(navigationInset)
        if (tableView.paddingBottom != chromeBottom) tableView.updatePadding(bottom = chromeBottom)
        if (fastScrollView.paddingBottom != chromeBottom) fastScrollView.setPadding(0, 0, 0, chromeBottom)
        val resultsBottom = chromeBottom + keyboardLift
        if (searchResultView.paddingBottom != resultsBottom) searchResultView.updatePadding(bottom = resultsBottom)
        if (noResultsView.paddingBottom != resultsBottom) noResultsView.setPadding(0, 0, 0, resultsBottom)
        if (searchFastScrollView.paddingBottom != resultsBottom) searchFastScrollView.setPadding(0, 0, 0, resultsBottom)
    }

    private fun applyKeyboardLift(ime: Int) {
        keyboardLiftIme = ime
        bottomContainer.translationY = tipCollapseOffset - (ime - navigationInset).coerceAtLeast(0)
    }

    private fun updateDockTip(animate: Boolean) {
        val tip = when {
            dockController.usedCopy -> DockView.Tip.Hidden
            dockController.usedCollect -> DockView.Tip.Copy
            else -> DockView.Tip.Collect
        }
        dockView.setTip(tip, animate)
    }

    private fun applySearchProgress(p: Float) {
        val tableScale = 1f - 0.08f * p
        tableLayer.scaleX = tableScale
        tableLayer.scaleY = tableScale
        tableDim.alpha = 0.2f * p
        tableDim.visibility = if (p > 0f) VISIBLE else GONE
        val resultsScale = 1.08f - 0.08f * p
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val fastScrollView = interceptedFastScrollView
        if (fastScrollView != null) {
            val fsEvent = MotionEvent.obtain(event)
            fastScrollView.getLocationOnScreen(tmpLocation)
            fsEvent.setLocation(event.rawX - tmpLocation[0], event.rawY - tmpLocation[1])
            fastScrollView.onTouchEvent(fsEvent)
            fsEvent.recycle()
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                interceptedFastScrollView = null
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun showBlockSelector() {
        if (tableController.blocks.isEmpty()) return

        val popup = popup ?: kotlin.run {
            val blockSelectorView = BlockSelectorView(context, tableController.blocks, blockSelectorDelegate)
            BlockSelectorPopup(blockSelectorView, wrapContent, wrapContent).also {
                popup = it
            }
        }

        val xOffset = (topBar.width - popup.view.calculateWidth()) / 2
        val yOffset = (-8).dp(context)
        val titleView = topBar.titleView
        val titleCenterX = titleView.left + (titleView.paddingLeft + titleView.width - titleView.paddingRight) / 2f
        popup.setAnchorX(titleCenterX - xOffset)
        popup.showAsDropDown(topBar, xOffset, yOffset, LEFT or TOP)
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
        if (!ValueAnimator.areAnimatorsEnabled()) {
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

    fun reloadChars() {
        launch {
            val chars = tableController.tableChars
            if (!tableController.loadChars(fast = false)) return@launch
            val topCodePoint = firstVisibleCodePoint(chars)
            val topOffset = tableView.firstVisibleRowOffset
            tableAdapter.notifyDataSetChanged()
            scrollToCodePoint(topCodePoint, topOffset)
            tableController.loadBlocks()
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

    private fun showSearch() {
        noResultsView.visibility = GONE
        isShowingRecents = true
        topBar.setSearchTitle(hasQuery = false)
        topBar.setSearchShown(true)
        searchController.recentResult = emptyArray()
        searchAdapter.notifyDataSetChanged()
        searchResultView.scrollToPosition(0)
        delegate.onSearchShown()
        searchJob?.cancel()
        searchJob = launch {
            searchController.loadRecents()
            searchAdapter.notifyDataSetChanged()
        }
    }

    private fun hideSearchInternal() {
        topBar.setSearchShown(false)
        searchJob?.cancel()
        searchResultView.stopScroll()
        delegate.onSearchHidden()
    }

    private fun runSearch(query: String) {
        if (!searchBar.isExpanded()) return
        topBar.setSearchTitle(hasQuery = query.isNotEmpty())
        searchJob?.cancel()
        searchJob = launch {
            if (query.isEmpty()) {
                isShowingRecents = true
                noResultsView.visibility = GONE
                searchResultView.stopScroll()
                searchController.loadRecents()
                searchResultView.scrollToPosition(0)
                searchAdapter.notifyDataSetChanged()
            } else {
                isShowingRecents = false
                searchResultView.stopScroll()
                searchController.search(query, 64)
                searchResultView.scrollToPosition(0)
                searchAdapter.notifyDataSetChanged()
                noResultsView.visibility = if (searchController.searchResult.isEmpty()) VISIBLE else GONE
                searchController.search(query)
                searchAdapter.notifyDataSetChanged()
                noResultsView.visibility = if (searchController.searchResult.isEmpty()) VISIBLE else GONE
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

    interface Delegate {
        fun onShowDetails(codePoint: CodePoint)

        fun onItemAdded(codePoint: CodePoint)

        fun onSearchShown()

        fun onSearchHidden()

        fun onSettingsClick()
    }
}
