package vadiole.unicode.ui.components

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import vadiole.unicode.AppComponent
import vadiole.unicode.ui.details.DetailsSheet
import vadiole.unicode.ui.table.TableController
import vadiole.unicode.ui.table.TableScreen
import vadiole.unicode.ui.theme.Theme
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_dialogDim
import vadiole.unicode.utils.dp
import vadiole.unicode.utils.frameParams
import vadiole.unicode.utils.ktx.isVisible
import vadiole.unicode.utils.ktx.with
import vadiole.unicode.utils.matchParent
import kotlin.math.abs
import kotlin.math.hypot

class NavigationView(context: Context, appComponent: AppComponent) : FrameLayout(context), ThemeDelegate {
    private val charStorage = appComponent.charsStorage
    private val theme = appComponent.theme

    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scaledMinimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var openAnimation: SpringAnimation? = null
    private var isOpenOrOpening = false
    private var touchDownX = -1f
    private var touchDownY = -1f
    private var touchDownTranslationY = -1f
    private var velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val maxOverdragY = 50f.dp(context)
    private val canDismissWithTouchOutside = true

    private val tableController = TableController(charStorage)
    private val tableDelegate = object : TableScreen.Delegate {
        override fun onItemClick(id: Int) {
            showDetailsBottomSheet(id)
        }
    }
    private val tableScreen = TableScreen(context, theme, tableController, tableDelegate)
    private val dimView = View(context).apply {
        layoutParams = frameParams(matchParent, matchParent)
        visibility = View.GONE
    }
    var detailsSheet: DetailsSheet? = null

    init {
        clipChildren = false
        isMotionEventSplittingEnabled = false
        Looper.getMainLooper().queue.addIdleHandler {
            detailsSheet = DetailsSheet(context, theme, charStorage).also { detailsSheet ->
                addView(dimView)
                addView(detailsSheet)
                detailsSheet.doOnPreDraw {
                    it.translationY = it.measuredHeight.toFloat()
                }
            }
            false
        }
        addView(tableScreen)
        theme.observe(this)
    }


    fun showDetailsBottomSheet(id: Int = -1, withVelocity: Float = 0f) = with(detailsSheet) {
        if (id != -1) {
            bind(id = id)
        }
        visibility = VISIBLE
        isOpenOrOpening = true
        startSpringAnimation(
            view = this,
            toPosition = 0,
            startVelocity = withVelocity
        )

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

    private fun startSpringAnimation(
        view: View, toPosition: Int, startVelocity: Float
    ) {
        openAnimation?.cancel()
        openAnimation = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).apply {
            spring = SpringForce(toPosition.toFloat()).apply {
                stiffness = 400f
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


    // TODO: add perform click 
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
                    val needOverdrag = content.translationY < 0
                    content.translationY = if (needOverdrag) {
                        val realOverdrag = touchDownTranslationY + deltaY
                        -normalize(-realOverdrag, maxOverdragY)
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
                    Log.d("VELOCITY", velocity.toString())
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


    private fun normalize(dy: Float, max: Float): Float {
        Log.d("OVERDRAG", "dy = $dy, max = $max")
        if (dy > max) {
            return max
        }
        return dy
    }

    override fun applyTheme(theme: Theme) {
        dimView.setBackgroundColor(theme.getColor(key_dialogDim))
    }
}