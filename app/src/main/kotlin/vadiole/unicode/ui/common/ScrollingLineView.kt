package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.ceil

class ScrollingLineView(context: Context) : View(context) {
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_SECONDARY.sp(context)
        typeface = font_regular
    }
    private val scroller = OverScroller(context)
    private val configuration = ViewConfiguration.get(context)
    private val touchSlop = configuration.scaledTouchSlop
    private val minFlingVelocity = configuration.scaledMinimumFlingVelocity
    private val maxFlingVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
    private val fadeLength = 24.dp(context)
    private var velocityTracker: VelocityTracker? = null
    private var layout: Layout? = null
    private var textWidth = 0
    private var textLeft = 0f
    private var textTop = 0f
    private var maxScroll = 0
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var isDragging = false
    private var surfaceColor = 0
    var text: CharSequence = ""
        set(value) {
            field = value
            textWidth = ceil(Layout.getDesiredWidth(value, paint)).toInt()
            layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, textWidth).setIncludePad(false).setMaxLines(1).build()
            scroller.forceFinished(true)
            requestLayout()
            place()
        }

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(fadeLength)
    }

    fun setColors(text: Int, surface: Int) {
        paint.color = text
        surfaceColor = surface
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = maxOf(suggestedMinimumHeight, (layout?.height ?: 0) + paddingTop + paddingBottom)
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = place()

    private fun place() {
        val available = width - paddingLeft - paddingRight
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        maxScroll = maxOf(0, textWidth - available)
        textLeft = paddingLeft + if (isRtl && maxScroll == 0) (available - textWidth).toFloat() else 0f
        textTop = paddingTop + (height - paddingTop - paddingBottom - (layout?.height ?: 0)) / 2f
        scrollTo(if (isRtl) maxScroll else 0, 0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return
        canvas.translate(textLeft, textTop)
        layout.draw(canvas)
    }

    override fun getSolidColor() = surfaceColor

    override fun computeHorizontalScrollRange() = width + maxScroll

    override fun getLeftFadingEdgeStrength() = minOf(1f, scrollX.toFloat() / fadeLength)

    override fun getRightFadingEdgeStrength() = minOf(1f, (maxScroll - scrollX).toFloat() / fadeLength)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (maxScroll == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x
                downY = event.y
                isDragging = false
                velocityTracker = VelocityTracker.obtain()
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    scrollTo((scrollX - (event.x - lastX)).toInt().coerceIn(0, maxScroll), 0)
                    lastX = event.x
                } else {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > touchSlop && dx > dy) {
                        isDragging = true
                        lastX = event.x
                    } else if (dy > touchSlop) {
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = velocityTracker
                if (tracker != null) {
                    tracker.addMovement(event)
                    tracker.computeCurrentVelocity(1000, maxFlingVelocity)
                    val velocity = tracker.xVelocity
                    if (isDragging && event.actionMasked == MotionEvent.ACTION_UP && abs(velocity) > minFlingVelocity) {
                        scroller.fling(scrollX, 0, -velocity.toInt(), 0, 0, maxScroll, 0, 0)
                        postInvalidateOnAnimation()
                    }
                    tracker.recycle()
                    velocityTracker = null
                }
                isDragging = false
            }
        }
        velocityTracker?.addMovement(event)
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scroller.forceFinished(true)
    }
}
