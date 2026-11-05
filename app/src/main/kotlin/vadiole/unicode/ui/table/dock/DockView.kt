package vadiole.unicode.ui.table.dock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.replaceAccessibilityAction
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.ceil
import vadiole.unicode.R
import vadiole.unicode.ui.common.IconButton
import vadiole.unicode.ui.common.TEXT_CAPTION
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.animatorsEnabled
import vadiole.unicode.ui.common.borderWidth
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.font_regular
import vadiole.unicode.ui.common.rippleDrawable
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick
import vadiole.unicode.ui.extension.onLongClick

class DockView(context: Context, private val delegate: Delegate) : LinearLayout(context) {
    interface Delegate {
        fun onBackspace()
        fun onClear()
        fun onCopyAll()
        fun onRestore(): Boolean
        fun onTipCollapse(offset: Float)
        fun onTipCollapsed()
    }

    enum class Tip { Copy, Hidden }

    companion object {
        private const val BOUNCE_VELOCITY = 6f

        fun glyphSize(context: Context): Float = 24f.dp(context)
    }

    val contentHeight = 56.dp(context)
    val chromeHeight: Int
        get() = contentHeight + if (tipView.visibility == VISIBLE) tipHeight else 0
    private val buttonWidth = 48.dp(context)
    private val edgePadding = 4.dp(context)
    private val backspaceExtraLeft = (contentHeight - buttonWidth) / 2
    private val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dividerHeight = borderWidth(context)
    private val dividerPaint = Paint().apply {
        color = context.getColor(R.color.floatingBorder)
    }
    private val tipView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_CAPTION)
        typeface = font_regular
        includeFontPadding = false
        gravity = Gravity.CENTER
        isSingleLine = true
        setPadding(16.dp(context), 0, 16.dp(context), 0)
        setTextColor(context.getColor(R.color.windowTextSecondary))
        visibility = GONE
    }
    private val tipHeight = maxOf(28.dp(context), tipView.paint.getFontMetricsInt(null) + 8.dp(context))
    private val emptyHint = TextView(context).apply {
        setText(R.string.dock_tip_collect)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SECONDARY)
        typeface = font_regular
        includeFontPadding = false
        gravity = Gravity.CENTER
        setPadding(16.dp(context), 4.dp(context), 16.dp(context), 4.dp(context))
        setTextColor(context.getColor(R.color.windowTextSecondary))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }
    private val textView = DockTextView(context).apply {
        setPadding(16.dp(context), 0, 8.dp(context), 0)
    }
    private var scrollToEndPending = false
    private var tapDownX = 0f
    private var isTapTracking = false
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressRunnable = Runnable {
        if (!isTapTracking) return@Runnable
        stopTap()
        performLongClick()
    }
    private var pendingScrollX = -1
    private val scrollView = object : HorizontalScrollView(context) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            if (pendingScrollX >= 0) {
                scrollTo(pendingScrollX, 0)
                pendingScrollX = -1
            } else if (scrollToEndPending) {
                scrollToEndPending = false
                scrollTo(textView.width, 0)
            }
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            trackTap(event)
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchDraw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipRect(scrollX.toFloat(), -height.toFloat(), (scrollX + width).toFloat(), height * 2f)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(count)
        }
    }.apply {
        clipChildren = false
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(textView, LayoutParams(wrapContent, matchParent))
    }
    private val backspaceButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_backspace)!!,
        size = buttonWidth,
        floating = false,
    ).apply {
        setPadding(backspaceExtraLeft, 0, 0, 0)
        contentDescription = context.getString(R.string.dock_backspace)
        onClick = { delegate.onBackspace() }
        onLongClick = { delegate.onClear() }
        replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_LONG_CLICK, context.getString(R.string.dock_clear_all), null)
    }
    private val row = FrameLayout(context).apply {
        clipChildren = false
        addView(scrollView, frameParams(matchParent, matchParent, marginRight = buttonWidth + edgePadding))
        addView(emptyHint, frameParams(matchParent, matchParent))
        addView(backspaceButton, frameParams(buttonWidth + backspaceExtraLeft, matchParent, Gravity.RIGHT, marginRight = edgePadding))
    }
    private var hasContent = true
    private var tip = Tip.Hidden
    private var tipAnimator: ValueAnimator? = null
    private val tmpLocation = IntArray(2)

    init {
        orientation = VERTICAL
        clipChildren = false
        background = context.rippleDrawable(
            content = ColorDrawable(context.getColor(R.color.layer1)),
            mask = ColorDrawable(Color.WHITE),
        )
        setWillNotDraw(false)
        isClickable = true
        contentDescription = context.getString(R.string.dock_copy_all)
        onClick = { if (hasContent) delegate.onCopyAll() }
        setOnLongClickListener { onLongPress() }
        addView(tipView, linearParams(matchParent, tipHeight))
        addView(row, linearParams(matchParent, contentHeight))
    }

    fun setBottomInset(inset: Int) {
        if (paddingBottom != inset) setPadding(0, 0, 0, inset)
    }

    val textWidth: Float
        get() = textView.textWidth

    fun setText(text: String, popFrom: Int = -1, hiddenFrom: Int = -1) {
        textView.setText(text, popFrom, hiddenFrom)
        scrollToEndPending = true
        scrollView.scrollTo(textView.width, 0)
        setHasContent(text.isNotEmpty())
    }

    fun reveal(end: Int) = textView.reveal(end, bounce = true)

    var scrollPosition: Int
        get() {
            val max = textView.width - scrollView.width
            return if (scrollToEndPending || scrollView.scrollX >= max) -1 else scrollView.scrollX
        }
        set(value) {
            if (value < 0) return
            scrollToEndPending = false
            pendingScrollX = value
            scrollView.requestLayout()
        }

    private fun onLongPress(): Boolean {
        if (!hasContent) return delegate.onRestore()
        delegate.onCopyAll()
        return true
    }

    fun getSlotCenter(slotLeft: Float, slotRight: Float, out: FloatArray) {
        textView.getLocationInWindow(tmpLocation)
        out[0] = tmpLocation[0] + (textView.paddingLeft + (slotLeft + slotRight) / 2f) * scaleX
        out[1] = tmpLocation[1] + textView.height / 2f
    }

    fun setTip(value: Tip, animate: Boolean) {
        if (tip == value) return
        tip = value
        tipAnimator?.removeAllListeners()
        tipAnimator?.cancel()
        tipAnimator = null
        val canAnimate = animate && isLaidOut && animatorsEnabled
        val hidden = value == Tip.Hidden
        if (!hidden) {
            if (tipView.visibility == GONE) setTipCollapse(1f)
            tipView.setText(R.string.dock_tip_copy)
            tipView.visibility = VISIBLE
        }
        if (canAnimate) {
            val start = -row.translationY / tipHeight
            val end = if (hidden) 1f else 0f
            tipAnimator = ValueAnimator.ofFloat(start, end).apply {
                duration = (250 * abs(end - start)).toLong()
                interpolator = easeOut
                addUpdateListener { setTipCollapse(it.animatedValue as Float) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        tipAnimator = null
                        if (hidden) hideTip()
                    }
                })
                start()
            }
        } else if (hidden) {
            hideTip()
        } else {
            setTipCollapse(0f)
        }
    }

    private fun setTipCollapse(fraction: Float) {
        val offset = tipHeight * fraction
        tipView.alpha = (1f - fraction / 0.6f).coerceAtLeast(0f)
        row.translationY = -offset
        delegate.onTipCollapse(offset)
    }

    private fun hideTip() {
        tipAnimator = null
        if (tipView.visibility == GONE) return
        tipView.visibility = GONE
        tipView.alpha = 1f
        row.translationY = 0f
        delegate.onTipCollapsed()
    }

    private fun trackTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapDownX = event.x
                isTapTracking = true
                drawableHotspotChanged(event.x + row.left + scrollView.left, event.y + row.top + scrollView.top)
                isPressed = hasContent
                postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> if (isTapTracking && abs(event.x - tapDownX) > touchSlop) stopTap()

            MotionEvent.ACTION_UP -> if (isTapTracking) {
                stopTap()
                if (hasContent) delegate.onCopyAll()
            }

            MotionEvent.ACTION_CANCEL -> stopTap()
        }
    }

    private fun stopTap() {
        isTapTracking = false
        isPressed = false
        removeCallbacks(longPressRunnable)
    }

    private fun setHasContent(value: Boolean) {
        if (hasContent == value) return
        val animate = isLaidOut && animatorsEnabled
        hasContent = value
        emptyHint.visibility = if (value) GONE else VISIBLE
        isClickable = value
        contentDescription = context.getString(if (value) R.string.dock_copy_all else R.string.dock_tip_collect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tooltipText = if (value) null else context.getString(R.string.dock_tip_collect)
        }
        backspaceButton.isEnabled = value
        backspaceButton.animate().cancel()
        val alpha = if (value) 1f else 0f
        val scale = if (value) 1f else 0.9f
        if (value) backspaceButton.visibility = VISIBLE
        if (animate) {
            backspaceButton.animate()
                .alpha(alpha)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .setInterpolator(easeOut)
                .withEndAction { if (!hasContent) backspaceButton.visibility = INVISIBLE }
                .start()
        } else {
            backspaceButton.alpha = alpha
            backspaceButton.scaleX = scale
            backspaceButton.scaleY = scale
            backspaceButton.visibility = if (value) VISIBLE else INVISIBLE
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), dividerHeight, dividerPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTap()
        backspaceButton.animate().cancel()
        tipView.animate().cancel()
        tipAnimator?.end()
    }

    private class DockTextView(context: Context) : View(context) {
        private val charPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = glyphSize(context)
            isSubpixelText = true
        }
        private val charColor = context.getColor(R.color.windowTextPrimary)
        private var text = ""
        var textWidth = 0f
            private set
        private var revealed = 0
        private var popStart = 0
        private var popStartWidth = 0f
        private var popEndWidth = 0f
        private var charBaseline = 0f
        private var popProgress = 1f
        private var isBounce = false
        private val bounceHolder = FloatValueHolder(1f)
        private val bounceSpring = SpringAnimation(bounceHolder).apply {
            spring = SpringForce(1f).apply {
                stiffness = 700f
                dampingRatio = 0.35f
            }
            setMinimumVisibleChange(0.001f)
            addUpdateListener { _, _, _ -> invalidate() }
            addEndListener { _, _, _, _ ->
                isBounce = false
                invalidate()
            }
        }
        private val popAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 120
            interpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
            addUpdateListener {
                popProgress = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            charPaint.color = charColor
        }

        fun setText(value: String, popFrom: Int, hiddenFrom: Int) {
            text = value
            textWidth = charPaint.measureText(value)
            when {
                hiddenFrom >= 0 -> {
                    revealed = minOf(revealed, hiddenFrom, value.length)
                    popStart = minOf(popStart, revealed)
                }

                popFrom in 0 until value.length -> {
                    revealed = minOf(revealed, popFrom)
                    reveal(value.length, bounce = false)
                }

                else -> {
                    popAnimator.cancel()
                    bounceSpring.cancel()
                    isBounce = false
                    popProgress = 1f
                    revealed = value.length
                }
            }
            requestLayout()
            invalidate()
        }

        fun reveal(end: Int, bounce: Boolean) {
            val target = minOf(end, text.length)
            if (target <= revealed) return
            popAnimator.cancel()
            bounceSpring.cancel()
            if (animatorsEnabled) {
                popStart = revealed
                popStartWidth = charPaint.measureText(text, 0, popStart)
                popEndWidth = charPaint.measureText(text, 0, target)
                revealed = target
                isBounce = bounce
                if (bounce) {
                    popProgress = 1f
                    bounceHolder.value = 1f
                    bounceSpring.setStartVelocity(BOUNCE_VELOCITY)
                    bounceSpring.start()
                } else {
                    popProgress = 0f
                    popAnimator.start()
                }
            } else {
                popProgress = 1f
                revealed = target
            }
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desired = paddingLeft + ceil(textWidth).toInt() + paddingRight
            setMeasuredDimension(
                resolveSize(desired.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec),
            )
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            charBaseline = (h - charPaint.descent() - charPaint.ascent()) / 2f
        }

        override fun onDraw(canvas: Canvas) {
            if (revealed <= 0) return
            val x = paddingLeft.toFloat()
            if ((popProgress >= 1f && !isBounce) || popStart >= revealed) {
                canvas.drawText(text, 0, revealed, x, charBaseline, charPaint)
                return
            }
            if (popStart > 0) canvas.drawText(text, 0, popStart, x, charBaseline, charPaint)
            val popX = x + popStartWidth
            val scale = if (isBounce) bounceHolder.value else 0.9f + 0.1f * popProgress
            val count = canvas.save()
            canvas.scale(scale, scale, popX + (popEndWidth - popStartWidth) / 2f, height / 2f)
            charPaint.alpha = (255 * popProgress).toInt()
            canvas.drawText(text, popStart, revealed, popX, charBaseline, charPaint)
            charPaint.color = charColor
            canvas.restoreToCount(count)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            popAnimator.cancel()
            bounceSpring.cancel()
            isBounce = false
            popProgress = 1f
        }
    }
}
