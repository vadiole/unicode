package vadiole.unicode.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import vadiole.unicode.UnicodeApp
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.details.DetailsSheet
import vadiole.unicode.ui.table.TableHelper
import vadiole.unicode.ui.table.TableScreen
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_dialogDim
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.isVisible
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.with
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot

class NavigationView(context: Context) : FrameLayout(context), ThemeDelegate {
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scaledMinimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var openAnimation: SpringAnimation? = null
    private var isOpenOrOpening = false
    private var touchDownX = -1f
    private var touchDownY = -1f
    private var touchDownTranslationY = -1f
    private var velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val maxOverdragY = 80f.dp(context)
    private val canDismissWithTouchOutside = true
    private var pendingCodePoint = CodePoint(-1)
    private var pendingCharSkipAnimation = false
    private val tableController = TableHelper(UnicodeApp.unicodeStorage, userConfig)
    private val tableDelegate = object : TableScreen.Delegate {
        override fun onItemClick(codePoint: CodePoint) {
            showDetailsBottomSheet(codePoint)
        }
    }
    private val tableScreen = TableScreen(context, tableController, tableDelegate)
    private val dimView = View(context).apply {
        layoutParams = frameParams(matchParent, matchParent)
        visibility = View.GONE
    }
    var detailsSheet: DetailsSheet? = null

    init {
        clipChildren = false
        isMotionEventSplittingEnabled = false
        addView(tableScreen)

        post {
            detailsSheet = DetailsSheet(context).also { detailsSheet ->
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
            themeManager.observe(this)
        }
        themeManager.observe(this)
    }

    fun showDetailsBottomSheet(codePoint: CodePoint = CodePoint(-1), withVelocity: Float = 0f, skipAnimation: Boolean = false) {
        val detailsSheet = detailsSheet
        if (detailsSheet != null) {
            if (codePoint.value >= 0) {
                detailsSheet.bind(codePoint = codePoint)
            }
            visibility = VISIBLE
            isOpenOrOpening = true
            if (skipAnimation) {
                detailsSheet.translationY = 0f
                doOnLayout {
                    updateDimBackground(0f, detailsSheet.measuredHeight)
                }
            } else {
                startSpringAnimation(
                    view = detailsSheet,
                    toPosition = 0,
                    startVelocity = withVelocity
                )
            }
        } else {
            pendingCodePoint = codePoint
            pendingCharSkipAnimation = skipAnimation
        }
    }

    fun hideDetailsBottomSheet(withVelocity: Float = 0f): Boolean {
        if (isOpenOrOpening) {
            with(detailsSheet) {
                isOpenOrOpening = false
                startSpringAnimation(this, measuredHeight, withVelocity)
                return true
            }
        }
        return false
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
            start()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val content = detailsSheet ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                touchDownTranslationY = content.translationY
                val isTouchOutside = event.rawY < content.top + content.translationY
                if (isTouchOutside) {
                    return isOpenOrOpening
                } else {
                    if (openAnimation?.isRunning == true) {
                        openAnimation?.cancel()
                        isOpenOrOpening = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val isTouchOutside = event.rawY < content.top + content.translationY
                if (isTouchOutside) {
                    return isOpenOrOpening
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
                if (isOpenOrOpening) {
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
                if (isOpenOrOpening) {
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

    override fun applyTheme() {
        dimView.setBackgroundColor(themeManager.getColor(key_dialogDim))
    }
}