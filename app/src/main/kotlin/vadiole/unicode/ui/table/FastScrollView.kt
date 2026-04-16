package vadiole.unicode.ui.table

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.ui.common.Squircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.roboto_semibold

class FastScrollView(
    context: Context,
    private val delegate: Delegate,
) : View(context) {

    interface Delegate {
        fun onFastScrollStart()
        fun onFastScroll(progress: Float, precisionLevel: Int)
        fun onFastScrollEnd()
        fun getBlockName(progress: Float): String?
    }

    // Dimensions
    private val thumbWidth = 6.dp(context)
    private val thumbMinHeight = 48.dp(context)
    private val thumbCornerRadius = thumbWidth / 2f
    private val touchAreaWidth = 44.dp(context)
    private val touchEdgeWidth = 20.dp(context)
    private val thumbMarginEnd = 4.dp(context)
    private val gripLineWidth = 4f.dp(context)
    private val gripLineHeight = 1f
    private val gripLineSpacing = 2f.dp(context)

    private val bubblePaddingHorizontal = 16f.dp(context)
    private val bubblePaddingVertical = 4f.dp(context)
    private val bubbleCornerRadius = 10f.dp(context)
    private val bubbleMarginEnd = 24.dp(context)
    private val bubbleTextSize = 15f.dp(context)
    private val bubbleArrowWidth = 12f.dp(context)
    private val bubbleArrowHeight = 20f.dp(context)
    private val bubbleMarginStart = 24f.dp(context)
    private val bubbleMinHeight = 48f.dp(context)
    private val arrowOverlap = 1f.dp(context)
    private val precisionThreshold1 = 100.dp(context)
    private val precisionThreshold2 = 200.dp(context)
    private val gestureExclusionHeight = thumbMinHeight * 2

    // Colors
    private val scrollIndicatorColor = context.getColor(R.color.scrollIndicator)
    private val scrollIndicatorActiveColor = context.getColor(R.color.scrollIndicatorActive)
    private val scrollIndicatorGripColor = context.getColor(R.color.scrollIndicatorGrip)

    // Paint objects
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = scrollIndicatorColor
    }

    private val bubbleShadowRadius = 6f.dp(context)
    private val bubbleShadowDy = 2f.dp(context)
    private val bubbleShadowColor = 0x2A000000
    private val bubbleShadowBaseAlpha = bubbleShadowColor ushr 24

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.dialogSurface)
        setShadowLayer(bubbleShadowRadius, 0f, bubbleShadowDy, bubbleShadowColor)
    }

    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = bubbleTextSize
        color = context.getColor(R.color.windowTextPrimary)
        typeface = roboto_semibold
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }

    // State
    private var scrollProgress = 0f
    private var isDragging = false
    private var dragTouchOffset: Float? = 0f
    private var bubbleAlpha = 0f
    private val thumbScaleActive = 7f / 6f
    private var thumbScale = 1f
    private var bubbleScale = 0.85f
    private var blockName: String? = null
    private var previousBlockName: String? = null
    private var precisionLevel = 0
    private var anchorY = 0f
    private var anchorProgress = 0f
    private var isPrecisionActive = false
    private var thumbHeightFraction = 0f
    private val gestureExclusionRect = mutableListOf<Rect>()

    // Throttle
    private val throttleIntervalMs = if (Build.VERSION.SDK_INT >= 34) 17L else 30L
    private var lastHapticTime = 0L
    private var lastDispatchTime = 0L
    private var pendingProgress = -1f

    // Cached rects
    private val thumbRect = RectF()
    private val bubbleRect = RectF()
    private val bubblePath = Path()
    private val arrowPath = Path()
    private val bubbleSquircle = Squircle(bubbleCornerRadius.toInt())
    private var cachedBubbleLeft: Float? = null
    private var cachedBubbleTop: Float? = null
    private var cachedBubbleRight: Float? = null
    private var cachedBubbleBottom: Float? = null
    private var cachedArrowCenterY: Float? = null

    // Pre-computed bubble geometry (set in calculateBubbleGeometry, read in drawBubble)
    private val bubbleFontMetrics = Paint.FontMetrics()
    private var bubbleTextX = 0f
    private var bubbleTextY = 0f
    private var bubbleDisplayName = ""
    private var bubblePivotX = 0f
    private var bubblePivotY = 0f
    private var bubbleGeometryValid = false

    // Animators
    private val argbEvaluator = ArgbEvaluator()
    private val thumbColorAnimator = ValueAnimator().apply {
        duration = 150
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            thumbPaint.color = animator.animatedValue as Int
            invalidate()
        }
    }

    private val thumbScaleAnimator = ValueAnimator().apply {
        duration = 200
        interpolator = OvershootInterpolator(1.5f)
        addUpdateListener { animator ->
            thumbScale = animator.animatedValue as Float
            invalidate()
        }
    }

    private val linearInterpolator = LinearInterpolator()
    private val bubbleScaleInterpolator = OvershootInterpolator(1.5f)

    private val bubbleAlphaAnimator = ValueAnimator().apply {
        interpolator = linearInterpolator
        addUpdateListener { animator ->
            bubbleAlpha = animator.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (bubbleAlpha == 0f) {
                    blockName = null
                }
            }
        })
    }

    private val bubbleScaleAnimator = ValueAnimator().apply {
        addUpdateListener { animator ->
            bubbleScale = animator.animatedValue as Float
            invalidate()
        }
    }

    private val thumbHeightAnimator = ValueAnimator().apply {
        duration = 200
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            thumbHeightFraction = animator.animatedValue as Float
            calculateThumbRect()
            if (isDragging) calculateBubbleGeometry()
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        thumbColorAnimator.cancel()
        thumbScaleAnimator.cancel()
        thumbHeightAnimator.cancel()
        bubbleAlphaAnimator.cancel()
        bubbleScaleAnimator.cancel()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun updateGestureExclusionRects() {
        val thumbCenterY = thumbRect.centerY().toInt()
        val exclusionHeight = maxOf(gestureExclusionHeight, thumbRect.height().toInt() * 2)
        val top = (thumbCenterY - exclusionHeight / 2).coerceAtLeast(0)
        val bottom = (top + exclusionHeight).coerceAtMost(height)
        gestureExclusionRect.clear()
        gestureExclusionRect.add(
            Rect(width - touchAreaWidth, top, width, bottom)
        )
        systemGestureExclusionRects = gestureExclusionRect
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateThumbRect()
        if (isDragging) {
            calculateBubbleGeometry()
        }
    }

    fun setScrollProgress(progress: Float) {
        if (isDragging) return
        scrollProgress = progress.coerceIn(0f, 1f)
        calculateThumbRect()
        invalidate()
    }

    private fun animateThumbScale(target: Float) {
        thumbScaleAnimator.cancel()
        thumbScaleAnimator.setFloatValues(thumbScale, target)
        thumbScaleAnimator.start()
    }

    private fun animateThumbColor(target: Int) {
        thumbColorAnimator.cancel()
        thumbColorAnimator.setIntValues(thumbPaint.color, target)
        thumbColorAnimator.setEvaluator(argbEvaluator)
        thumbColorAnimator.start()
    }

    private fun animateBubble(target: Float) {
        bubbleAlphaAnimator.cancel()
        bubbleScaleAnimator.cancel()
        val isShow = target > bubbleAlpha
        val targetScale = if (isShow) 1f else 0.85f

        bubbleAlphaAnimator.duration = if (isShow) 150 else 100
        bubbleAlphaAnimator.setFloatValues(bubbleAlpha, target)
        bubbleAlphaAnimator.start()

        bubbleScaleAnimator.duration = if (isShow) 250 else 200
        bubbleScaleAnimator.interpolator = bubbleScaleInterpolator
        bubbleScaleAnimator.setFloatValues(bubbleScale, targetScale)
        bubbleScaleAnimator.start()
    }

    private inline fun withTrackMetrics(block: (trackTop: Float, thumbHeight: Float, availableTrack: Float) -> Unit) {
        val trackTop = paddingTop.toFloat()
        val trackBottom = height.toFloat() - paddingBottom
        val trackHeight = trackBottom - trackTop
        if (trackHeight <= 0) return
        val thumbHeight = thumbMinHeight * (1f + thumbHeightFraction)
        val availableTrack = trackHeight - thumbHeight
        if (availableTrack <= 0) return
        block(trackTop, thumbHeight, availableTrack)
    }

    private fun calculateThumbRect() {
        withTrackMetrics { trackTop, thumbHeight, availableTrack ->
            val thumbTop = trackTop + availableTrack * scrollProgress
            val currentThumbWidth = thumbWidth.toFloat()
            val left = width - currentThumbWidth - thumbMarginEnd
            thumbRect.set(left, thumbTop, left + currentThumbWidth, thumbTop + thumbHeight)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            updateGestureExclusionRects()
        }
    }

    private fun calculateBubbleGeometry() {
        val name = blockName
        if (name == null) {
            bubbleGeometryValid = false
            return
        }

        bubbleTextPaint.getFontMetrics(bubbleFontMetrics)
        val textHeight = bubbleFontMetrics.descent - bubbleFontMetrics.ascent

        val bubbleRight = thumbRect.left - bubbleMarginEnd - bubbleArrowWidth
        val maxBubbleWidth = bubbleRight - bubbleMarginStart
        if (maxBubbleWidth <= 0) {
            bubbleGeometryValid = false
            return
        }
        val maxTextWidth = maxBubbleWidth - bubblePaddingHorizontal * 2
        val textWidth = bubbleTextPaint.measureText(name).coerceAtMost(maxTextWidth)
        val bubbleWidth = textWidth + bubblePaddingHorizontal * 2
        val bubbleLeft = bubbleRight - bubbleWidth

        val contentHeight = textHeight + bubblePaddingVertical * 2
        val bubbleHeight = maxOf(contentHeight, bubbleMinHeight)

        val thumbCenterY = thumbRect.centerY()
        val bubbleTop = thumbCenterY - bubbleHeight / 2

        bubbleRect.set(bubbleLeft, bubbleTop, bubbleRight, bubbleTop + bubbleHeight)

        // Clamp bubble to view bounds
        val topBound = paddingTop.toFloat()
        val bottomBound = height.toFloat() - paddingBottom
        if (bubbleRect.top < topBound) {
            bubbleRect.offset(0f, topBound - bubbleRect.top)
        }
        if (bubbleRect.bottom > bottomBound) {
            bubbleRect.offset(0f, -(bubbleRect.bottom - bottomBound))
        }

        // Build path with arrow (cached)
        val arrowCenterY = thumbCenterY.coerceIn(
            bubbleRect.top + maxOf(bubbleCornerRadius, bubbleArrowHeight / 2),
            bubbleRect.bottom - maxOf(bubbleCornerRadius, bubbleArrowHeight / 2)
        )

        if (bubbleRect.left != cachedBubbleLeft ||
            bubbleRect.top != cachedBubbleTop ||
            bubbleRect.right != cachedBubbleRight ||
            bubbleRect.bottom != cachedBubbleBottom ||
            arrowCenterY != cachedArrowCenterY
        ) {
            cachedBubbleLeft = bubbleRect.left
            cachedBubbleTop = bubbleRect.top
            cachedBubbleRight = bubbleRect.right
            cachedBubbleBottom = bubbleRect.bottom
            cachedArrowCenterY = arrowCenterY

            bubbleSquircle.setBounds(
                bubbleRect.left.toInt(), bubbleRect.top.toInt(),
                bubbleRect.right.toInt(), bubbleRect.bottom.toInt()
            )

            val halfArrow = bubbleArrowHeight / 2f
            val baseX = bubbleRect.right - arrowOverlap
            val tipX = bubbleRect.right + bubbleArrowWidth

            arrowPath.reset()
            arrowPath.moveTo(baseX, arrowCenterY - halfArrow)
            arrowPath.cubicTo(
                baseX, arrowCenterY - halfArrow * 0.5f,
                tipX, arrowCenterY - halfArrow * 0.3f,
                tipX, arrowCenterY
            )
            arrowPath.cubicTo(
                tipX, arrowCenterY + halfArrow * 0.3f,
                baseX, arrowCenterY + halfArrow * 0.5f,
                baseX, arrowCenterY + halfArrow
            )
            arrowPath.close()

            bubblePath.reset()
            bubblePath.addPath(bubbleSquircle.path)
            bubblePath.op(arrowPath, Path.Op.UNION)
        }

        // Pre-compute text drawing coordinates
        bubbleTextX = bubbleRect.left + bubblePaddingHorizontal
        bubbleTextY = bubbleRect.centerY() - (bubbleFontMetrics.ascent + bubbleFontMetrics.descent) / 2
        bubbleDisplayName = if (bubbleTextPaint.measureText(name) > maxTextWidth) {
            ellipsizeText(name, maxTextWidth)
        } else {
            name
        }
        bubblePivotX = bubbleRect.right + bubbleArrowWidth
        bubblePivotY = arrowCenterY

        bubbleGeometryValid = true
    }

    internal fun isInEdgeZone(x: Float): Boolean {
        return x >= width - touchEdgeWidth
    }

    internal fun isInThumbZone(x: Float, y: Float): Boolean {
        if (x < width - touchAreaWidth) return false
        val centerY = thumbRect.centerY()
        val halfZone = gestureExclusionHeight / 2f
        return y >= centerY - halfZone && y <= centerY + halfZone
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (height <= 0 || width <= 0) return

        // Draw thumb
        canvas.save()
        canvas.scale(thumbScale, thumbScale, thumbRect.centerX(), thumbRect.centerY())
        canvas.drawRoundRect(thumbRect, thumbCornerRadius, thumbCornerRadius, thumbPaint)

        // Grip lines
        val cx = thumbRect.centerX()
        val cy = thumbRect.centerY()
        val halfW = gripLineWidth / 2f
        val halfH = gripLineHeight / 2f
        val halfGap = gripLineSpacing / 2f
        val savedColor = thumbPaint.color
        thumbPaint.color = scrollIndicatorGripColor
        canvas.drawRoundRect(cx - halfW, cy - halfGap - halfH, cx + halfW, cy - halfGap + halfH, halfH, halfH, thumbPaint)
        canvas.drawRoundRect(cx - halfW, cy + halfGap - halfH, cx + halfW, cy + halfGap + halfH, halfH, halfH, thumbPaint)
        thumbPaint.color = savedColor
        canvas.restore()

        // Draw bubble
        if (bubbleAlpha > 0f && blockName != null) {
            drawBubble(canvas)
        }
    }

    private fun drawBubble(canvas: Canvas) {
        if (!bubbleGeometryValid) return

        val savedAlpha = bubblePaint.alpha
        val savedTextAlpha = bubbleTextPaint.alpha
        val alpha = (bubbleAlpha * 255).toInt()
        bubblePaint.alpha = alpha
        bubbleTextPaint.alpha = alpha

        val modulatedShadowAlpha = (bubbleShadowBaseAlpha * bubbleAlpha).toInt()
        bubblePaint.setShadowLayer(bubbleShadowRadius, 0f, bubbleShadowDy, modulatedShadowAlpha shl 24)

        canvas.save()
        canvas.translate(bubblePivotX, bubblePivotY)
        canvas.scale(bubbleScale, bubbleScale)
        canvas.translate(-bubblePivotX, -bubblePivotY)

        canvas.drawPath(bubblePath, bubblePaint)
        canvas.drawText(bubbleDisplayName, bubbleTextX, bubbleTextY, bubbleTextPaint)

        canvas.restore()

        bubblePaint.alpha = savedAlpha
        bubbleTextPaint.alpha = savedTextAlpha
        bubblePaint.setShadowLayer(bubbleShadowRadius, 0f, bubbleShadowDy, bubbleShadowColor)
    }

    private fun ellipsizeText(text: String, maxWidth: Float): String {
        val ellipsis = "\u2026"
        val ellipsisWidth = bubbleTextPaint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && bubbleTextPaint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isInEdgeZone(event.x)) {
                    isDragging = true
                    if (!userConfig.usedFastScroll) {
                        userConfig.usedFastScroll = true
                    }
                    isPrecisionActive = false
                    precisionLevel = 0
                    thumbHeightAnimator.cancel()
                    thumbHeightFraction = 0f
                    lastHapticTime = 0L
                    lastDispatchTime = 0L
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (isInThumbZone(event.x, event.y)) {
                        dragTouchOffset = null
                        blockName = delegate.getBlockName(scrollProgress)
                        calculateBubbleGeometry()
                    } else {
                        dragTouchOffset = 0f
                        updateProgressFromTouch(event.y, event.x)
                    }
                    animateThumbColor(scrollIndicatorActiveColor)
                    animateThumbScale(thumbScaleActive)
                    animateBubble(1f)
                    delegate.onFastScrollStart()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    if (dragTouchOffset == null) {
                        dragTouchOffset = event.y - thumbRect.centerY()
                    }
                    updateProgressFromTouch(event.y - (dragTouchOffset ?: 0f), event.x)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    if (pendingProgress >= 0f) {
                        delegate.onFastScroll(pendingProgress, precisionLevel)
                        pendingProgress = -1f
                    }
                    isDragging = false
                    isPrecisionActive = false
                    precisionLevel = 0
                    previousBlockName = null
                    cachedBubbleLeft = null
                    cachedBubbleTop = null
                    cachedBubbleRight = null
                    cachedBubbleBottom = null
                    cachedArrowCenterY = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    animateBubble(0f)
                    animateThumbColor(scrollIndicatorColor)
                    animateThumbScale(1f)
                    animateThumbHeight(0)
                    delegate.onFastScrollEnd()
                    return true
                }
            }
        }
        return false
    }

    private fun precisionLevelFromX(eventX: Float): Int {
        val distanceFromRight = (width - eventX).coerceAtLeast(0f)
        return when {
            distanceFromRight >= precisionThreshold2 -> 2
            distanceFromRight >= precisionThreshold1 -> 1
            else -> 0
        }
    }

    private fun precisionSpeedFactor(level: Int): Float = when (level) {
        1 -> 0.25f
        2 -> 0.0625f
        else -> 1f
    }

    private fun animateThumbHeight(targetLevel: Int) {
        thumbHeightAnimator.cancel()
        thumbHeightAnimator.setFloatValues(thumbHeightFraction, targetLevel.toFloat())
        thumbHeightAnimator.start()
    }

    private fun onPrecisionLevelChanged(oldLevel: Int, newLevel: Int) {
        animateThumbHeight(newLevel)
        val haptic = HapticFeedbackConstants.LONG_PRESS
        performHapticFeedback(haptic)
    }

    private fun updateProgressFromTouch(y: Float, eventX: Float) {
        withTrackMetrics { trackTop, thumbHeight, availableTrack ->
            val newLevel = precisionLevelFromX(eventX)

            if (!isPrecisionActive) {
                val progress = ((y - trackTop - thumbHeight / 2) / availableTrack).coerceIn(0f, 1f)
                scrollProgress = progress
                anchorY = y
                anchorProgress = progress
                precisionLevel = newLevel
                isPrecisionActive = true
            } else {
                if (newLevel != precisionLevel) {
                    anchorY = y
                    anchorProgress = scrollProgress
                    val oldLevel = precisionLevel
                    precisionLevel = newLevel
                    onPrecisionLevelChanged(oldLevel, newLevel)
                }
                val speedFactor = precisionSpeedFactor(precisionLevel)
                val deltaY = y - anchorY
                val deltaProgress = (deltaY / availableTrack) * speedFactor
                scrollProgress = (anchorProgress + deltaProgress).coerceIn(0f, 1f)
            }

            calculateThumbRect()
            val newBlockName = delegate.getBlockName(scrollProgress)
            if (newBlockName != null && newBlockName != previousBlockName) {
                val now = SystemClock.uptimeMillis()
                if (now - lastHapticTime >= throttleIntervalMs) {
                    val hapticConstant = if (Build.VERSION.SDK_INT >= 34) {
                        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                    performHapticFeedback(hapticConstant)
                    lastHapticTime = now
                }
            }
            previousBlockName = newBlockName
            blockName = newBlockName
            calculateBubbleGeometry()
            val now = SystemClock.uptimeMillis()
            if (now - lastDispatchTime >= throttleIntervalMs) {
                pendingProgress = -1f
                delegate.onFastScroll(scrollProgress, precisionLevel)
                lastDispatchTime = now
            } else {
                pendingProgress = scrollProgress
            }
            invalidate()
        }
    }
}
