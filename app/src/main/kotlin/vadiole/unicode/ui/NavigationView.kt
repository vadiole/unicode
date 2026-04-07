package vadiole.unicode.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.window.BackEvent
import androidx.core.view.doOnLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.unicodeStorage
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.details.DetailsSheet
import vadiole.unicode.ui.extension.isVisible
import vadiole.unicode.ui.table.TableController
import vadiole.unicode.ui.table.TableScreen
import vadiole.unicode.ui.table.search.SearchController

class NavigationView(context: Context) : FrameLayout(context), OnBackHandler {
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scaledMinimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var openAnimation: SpringAnimation? = null
    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var isDetailsOpenOrOpening = false
    private var isBackGestureInProgress = false
    private var touchDownX = -1f
    private var touchDownY = -1f
    private var touchDownTranslationY = -1f
    private var velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val maxOverdragY = 80f.dp(context)
    private val canDismissWithTouchOutside = true
    private var pendingCodePoint = CodePoint(-1)
    private var pendingCharSkipAnimation = false
    private val tableController = TableController(unicodeStorage, userConfig)
    private val searchController = SearchController(unicodeStorage)
    private val tableDelegate = object : TableScreen.Delegate {
        override fun onItemClick(codePoint: CodePoint) {
            showDetailsBottomSheet(codePoint)
        }

        override fun onSearchFocused() {
            updateBackEnabled()
        }

        override fun onSearchUnfocused() {
            updateBackEnabled()
        }
    }
    private val tableScreen = TableScreen(context, tableController, searchController, tableDelegate)
    private val dimView = View(context).apply {
        layoutParams = frameParams(matchParent, matchParent)
        visibility = GONE
    }
    private val detailsDelegate = object : DetailsSheet.Delegate {
        override fun findInTable(codePoint: CodePoint) {
            hideDetailsBottomSheet()
            tableScreen.hideSearch()
            tableScreen.scrollToChar(codePoint)
        }
    }
    var detailsSheet: DetailsSheet? = null

    init {
        clipChildren = false
        isMotionEventSplittingEnabled = false
        addView(tableScreen)

        post {
            detailsSheet = DetailsSheet(context, detailsDelegate).also { detailsSheet ->
                addView(dimView)
                addView(detailsSheet)
                if (!pendingCharSkipAnimation) {
                    detailsSheet.doOnLayout {
                        it.translationY = it.measuredHeight.toFloat()
                    }
                }
            }
            requestApplyInsets()
            if (pendingCodePoint.value >= 0) {
                showDetailsBottomSheet(pendingCodePoint, skipAnimation = pendingCharSkipAnimation)
            }
            dimView.setBackgroundColor(this.context.getColor(R.color.dialogDim))
        }

    }

    fun showDetailsBottomSheet(codePoint: CodePoint = CodePoint(-1), withVelocity: Float = 0f, skipAnimation: Boolean = false) {
        val detailsSheet = detailsSheet
        if (detailsSheet != null) {
            if (codePoint.value >= 0) {
                detailsSheet.bind(codePoint = codePoint, abbreviations = tableController.abbreviations)
            }
            visibility = VISIBLE
            isDetailsOpenOrOpening = true
            if (skipAnimation) {
                detailsSheet.translationY = 0f
                doOnLayout {
                    updateDimBackground(0f, detailsSheet.measuredHeight)
                }
            } else {
                startSpringAnimation(
                    view = detailsSheet,
                    toPosition = 0,
                    startVelocity = withVelocity,
                )
            }
        } else {
            pendingCodePoint = codePoint
            pendingCharSkipAnimation = skipAnimation
        }
        updateBackEnabled()
    }

    fun hideDetailsBottomSheet(withVelocity: Float = 0f) {
        if (!isDetailsOpenOrOpening) return
        val detailsSheet = detailsSheet ?: return
        isDetailsOpenOrOpening = false
        startSpringAnimation(detailsSheet, detailsSheet.measuredHeight, withVelocity)
    }

    private fun startSpringAnimation(view: View, toPosition: Int, startVelocity: Float) {
        openAnimation?.cancel()
        openAnimation = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).apply {
            spring = SpringForce(toPosition.toFloat()).apply {
                stiffness = 600f
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            setStartVelocity(startVelocity)
            addUpdateListener { _, translationY, _ ->
                updateDimBackground(translationY, view.measuredHeight)
            }
            addEndListener { _, _, _, _ ->
                updateBackEnabled()
            }
            start()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isBackGestureInProgress) return false
        val content = detailsSheet ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchDownTranslationY = content.translationY
                val isTouchOutside = event.rawY < content.top + content.translationY
                if (isTouchOutside) {
                    return isDetailsOpenOrOpening
                } else {
                    if (openAnimation?.isRunning == true) {
                        openAnimation?.cancel()
                        isDetailsOpenOrOpening = true
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val isTouchOutside = event.rawY < content.top + content.translationY
                if (isTouchOutside) {
                    return isDetailsOpenOrOpening
                }
                val dX = event.rawX - touchDownX
                val dY = event.rawY - touchDownY

                val dTotal = hypot(dX, dY)
                if (dTotal > scaledTouchSlop) {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    touchDownTranslationY = content.translationY
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isBackGestureInProgress) return false
        val content = detailsSheet ?: return false
        val deltaY = event.rawY - touchDownY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isTouchOutside = event.rawY < content.top + content.translationY
                if (isTouchOutside) {
                    if (canDismissWithTouchOutside) {
                        hideDetailsBottomSheet()
                        return true
                    }
                    touchDownTranslationY = content.translationY
                    velocityTracker.addMovement(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDetailsOpenOrOpening) {
                    val needOverdrag = content.translationY + deltaY < 0
                    content.translationY = if (needOverdrag) {
                        val realOverdrag = touchDownTranslationY + deltaY
                        -normalizeOverdrag(-realOverdrag, maxOverdragY)
                    } else {
                        touchDownTranslationY + deltaY
                    }
                    updateDimBackground(content.translationY, content.measuredHeight)
                    velocityTracker.addMovement(event)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDetailsOpenOrOpening) {
                    val pointerId: Int = event.getPointerId(event.actionIndex)
                    velocityTracker.computeCurrentVelocity(1000)
                    val velocity = velocityTracker.getYVelocity(pointerId)
                    if (abs(velocity) > scaledMinimumFlingVelocity) {
                        if (velocity > 0) {
                            hideDetailsBottomSheet(withVelocity = velocity)
                        } else {
                            val isOverdrag = content.translationY < 0
                            if (isOverdrag) {
                                showDetailsBottomSheet(withVelocity = 0f)
                            } else {
                                showDetailsBottomSheet(withVelocity = velocity)
                            }
                        }
                    } else {
                        if (content.translationY > content.measuredHeight * 0.5f) {
                            hideDetailsBottomSheet()
                        } else {
                            showDetailsBottomSheet()
                        }
                    }
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun updateDimBackground(translationY: Float, height: Int) {
        val percentDone = (height - translationY) / height * 0.6f
        dimView.alpha = percentDone
        dimView.isVisible = percentDone > 0
    }

    private fun normalizeOverdrag(dy: Float, max: Float): Float {
        return (2 * max * atan(0.5 * PI * dy / max) / PI).toFloat()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        detailsSheet?.onApplyWindowInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    override val isBackEnabled = MutableStateFlow(false)

    override fun onBackStarted(backEvent: BackEvent) {
        if (isDetailsOpenOrOpening) {
            openAnimation?.cancel()
            scaleXAnimation?.cancel()
            scaleYAnimation?.cancel()
            isBackGestureInProgress = true
            val sheet = detailsSheet ?: return
            sheet.pivotX = sheet.measuredWidth / 2f
            sheet.pivotY = 0f
            sheet.scaleX = 1f
            sheet.scaleY = 1f
            sheet.translationY = 0f
        }
    }

    override fun onBackProgressed(backEvent: BackEvent) {
        if (!isBackGestureInProgress) return
        val sheet = detailsSheet ?: return
        val progress = backEvent.progress

        val scale = 1f - progress * 0.1f
        sheet.scaleX = scale
        sheet.scaleY = scale

        val maxTranslation = sheet.measuredHeight * 0.1f
        sheet.translationY = progress * maxTranslation
        updateDimBackground(sheet.translationY, sheet.measuredHeight)
    }

    override fun onBackCancelled() {
        if (!isBackGestureInProgress) return
        isBackGestureInProgress = false
        val sheet = detailsSheet ?: return

        scaleXAnimation = SpringAnimation(sheet, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce(1f).apply {
                stiffness = 600f
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            start()
        }
        scaleYAnimation = SpringAnimation(sheet, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce(1f).apply {
                stiffness = 600f
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            start()
        }
        startSpringAnimation(sheet, toPosition = 0, startVelocity = 0f)
    }

    override fun onBackInvoked() {
        when {
            isDetailsOpenOrOpening -> {
                isBackGestureInProgress = false
                detailsSheet?.let { sheet ->
                    sheet.scaleX = 1f
                    sheet.scaleY = 1f
                }
                hideDetailsBottomSheet()
            }

            tableScreen.isSearchVisible() -> {
                tableScreen.hideSearch()
                updateBackEnabled()
            }
        }
    }

    private fun updateBackEnabled() {
        isBackEnabled.value = isDetailsOpenOrOpening || tableScreen.isSearchVisible()
    }
}
