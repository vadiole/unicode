package vadiole.unicode.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import vadiole.unicode.R

class Slider(context: Context, val range: ClosedFloatingPointRange<Float>) : View(context) {
    var onValueChanged: ((Float) -> Unit)? = null
    var onInteractionChanged: ((Boolean) -> Unit)? = null
    var onPressedChanged: ((Boolean) -> Unit)? = null
    var formatValue: ((Float) -> String)? = null
    var detent = Float.NaN

    var value: Float
        get() = current
        set(value) {
            current = value.coerceIn(range)
            invalidateShapes()
            updateExclusionRects()
        }
    private var current = range.start

    private val span = range.endInclusive - range.start
    private val trackHeight = 8f.dp(context)
    private val gap = 2f.dp(context)
    private val pressedGap = 1f.dp(context)
    private val capsuleWidth = 16f.dp(context)
    private val capsuleHeight = 24f.dp(context)
    private val pressedBorder = 2f.dp(context)
    private val lineWidth = 2f.dp(context)
    private val lineHeight = 10f.dp(context)
    private val humFullSpeed = 1500f.dp(context)

    private val activeColor = context.getColor(R.color.primaryControl)
    private val inactiveColor = context.getColor(R.color.windowDivider)
    private val lineColor = context.getColor(R.color.layer0)
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activeColor }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inactiveColor }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp(context)
        color = ColorUtils.setAlphaComponent(activeColor, FOCUS_ALPHA)
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val holeCoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private val activePath = Path()
    private val inactivePath = Path()
    private val ringPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val holePath = Path()
    private val focusPath = Path()
    private val focusOffset = 3f.dp(context)
    private val shapeRect = RectF()
    private val segmentRect = RectF()
    private val radii = FloatArray(8)
    private var shapesDirty = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val thumbGrabHalf = max(capsuleWidth / 2f, 22f.dp(context))
    private val exclusionHalf = 24.dp(context)
    private val exclusionRect = Rect()
    private val exclusionRects = listOf(exclusionRect)
    private var state = IDLE
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var grabOffset = 0f
    private var lastMoveX = 0f
    private var lastMoveTime = 0L
    private var speed = 0f
    private var interacting = false
    private var velocityTracker: VelocityTracker? = null
    private var flingingToDetent = false
    private var springTarget = 0f
    private val flingMinVelocity = 1000f.dp(context)

    private val click = TickHaptic(this, 0.8f)
    private val clickIntervalMs = if (Build.VERSION.SDK_INT >= 34) 17L else 30L
    private var lastClickTime = 0L
    private var lastHumTime = 0L
    private var lastHumStep = 0
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else null
    }
    private val humPrimitiveSupported by lazy {
        Build.VERSION.SDK_INT >= 33 && vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
    }
    private val touchAttributes by lazy {
        if (Build.VERSION.SDK_INT >= 33) VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH) else null
    }
    private val humEffects = arrayOfNulls<Any>(HUM_BUCKETS)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private var announced: String? = null

    private var press = 0f
    private val pressSpring = SpringAnimator(
        SPRING_STIFFNESS,
        PRESS_DAMPING,
        minVisibleChange = 0.002f,
        onUpdate = {
            press = it
            invalidateShapes()
        },
    )
    private val valueSpring = SpringAnimator(
        SPRING_STIFFNESS,
        minVisibleChange = 0.0005f,
        onUpdate = { applyUserValue((range.start + it * span).coerceIn(range), haptics = false) },
        onEnd = { canceled ->
            if (!canceled) {
                applyUserValue(springTarget, haptics = false)
                if (flingingToDetent) click.perform()
            }
            flingingToDetent = false
            if (state == IDLE) {
                setInteracting(false)
                updateExclusionRects()
            }
        },
    )

    init {
        require(range.endInclusive > range.start)
        isFocusable = true
        minimumHeight = 48.dp(context)
    }

    private fun invalidateShapes() {
        shapesDirty = true
        invalidate()
    }

    private val minX get() = paddingLeft + capsuleWidth / 2f
    private val maxX get() = width - paddingRight - capsuleWidth / 2f

    private fun valueX() = minX + (value - range.start) / span * (maxX - minX)

    private fun valueAt(x: Float): Float {
        val fraction = ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
        val raw = range.start + fraction * span
        return if (!detent.isNaN() && abs(raw - detent) <= DETENT_BAND * span / 2f) detent else raw
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        shapesDirty = true
        if (changed) updateExclusionRects()
    }

    private fun updateExclusionRects() {
        if (Build.VERSION.SDK_INT < 29 || width == 0) return
        val x = valueX().toInt()
        exclusionRect.set(x - exclusionHalf, 0, x + exclusionHalf, height)
        systemGestureExclusionRects = exclusionRects
    }

    private fun layoutShapes() {
        shapesDirty = false
        val p = press.coerceIn(0f, 1f)
        val scale = 1f + (PRESSED_SCALE - 1f) * press
        val half = capsuleWidth * scale / 2f
        val h = capsuleHeight * scale
        val radius = minOf(half, h / 2f)
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val x0 = valueX()
        val falloff = capsuleWidth * max(1f, PRESSED_SCALE - 1f)
        val nearStart = proximity(x0 - minX, falloff)
        val nearEnd = proximity(maxX - x0, falloff)
        val cx = x0 + (half - capsuleWidth / 2f) * (nearStart - nearEnd)
        val cy = (paddingTop + height - paddingBottom) / 2f
        val holeHalfWidth = lineWidth / 2f + (half - pressedBorder - lineWidth / 2f) * p
        val holeHalfHeight = lineHeight / 2f + (h / 2f - pressedBorder - lineHeight / 2f) * p
        val cut = gap + (pressedGap - gap) * p
        val edge = x0 + (left - x0) * nearStart + (right - x0) * nearEnd

        activePath.rewind()
        inactivePath.rewind()
        val outer = trackHeight / 2f
        piece(left, minOf(cx - half - cut, right), edge, cy, outer, 0f)
        val inner = (holeHalfWidth + half) / 2f
        piece(max(cx - inner, left), minOf(cx + inner, right), edge, cy, 0f, 0f)
        piece(max(cx + half + cut, left), right, edge, cy, 0f, outer)

        shapeRect.set(cx - half, cy - h / 2f, cx + half, cy + h / 2f)
        focusPath.rewind()
        if (isFocused) {
            shapeRect.inset(-focusOffset, -focusOffset)
            focusPath.addSquircle(shapeRect, radius + focusOffset)
            shapeRect.inset(focusOffset, focusOffset)
        }
        ringPath.reset()
        ringPath.addSquircle(shapeRect, radius)
        holePath.rewind()
        shapeRect.set(cx - holeHalfWidth, cy - holeHalfHeight, cx + holeHalfWidth, cy + holeHalfHeight)
        holePath.addSquircle(shapeRect, minOf(holeHalfWidth, holeHalfHeight))
        ringPath.addPath(holePath)
        val holeFade = minOf(1f, p / HOLE_FADE)
        holePaint.alpha = (HOLE_ALPHA * holeFade).toInt()
        holeCoverPaint.alpha = ((1f - holeFade) * 255).toInt()
    }

    private fun proximity(distance: Float, falloff: Float): Float {
        val t = 1f - (distance / falloff).coerceIn(0f, 1f)
        return t * t
    }

    private fun piece(left: Float, right: Float, edge: Float, cy: Float, leftRadius: Float, rightRadius: Float) {
        if (right <= left) return
        if (edge > left) addSegment(activePath, left, minOf(edge, right), cy, leftRadius, if (edge >= right) rightRadius else 0f)
        if (edge < right) addSegment(inactivePath, max(edge, left), right, cy, if (edge <= left) leftRadius else 0f, rightRadius)
    }

    private fun addSegment(path: Path, left: Float, right: Float, cy: Float, leftRadius: Float, rightRadius: Float) {
        radii[0] = leftRadius
        radii[1] = leftRadius
        radii[2] = rightRadius
        radii[3] = rightRadius
        radii[4] = rightRadius
        radii[5] = rightRadius
        radii[6] = leftRadius
        radii[7] = leftRadius
        segmentRect.set(left, cy - trackHeight / 2f, right, cy + trackHeight / 2f)
        path.addRoundRect(segmentRect, radii, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (shapesDirty) layoutShapes()
        canvas.drawPath(activePath, activePaint)
        canvas.drawPath(inactivePath, inactivePaint)
        if (holePaint.alpha > 0) canvas.drawPath(holePath, holePaint)
        canvas.drawPath(ringPath, activePaint)
        if (holeCoverPaint.alpha > 0) canvas.drawPath(holePath, holeCoverPaint)
        if (isFocused) canvas.drawPath(focusPath, focusPaint)
        if (DebugLayout.isShown) drawDebug(canvas)
    }

    private val debugPaint by lazy { Paint() }
    private var debugShown = false

    private fun drawDebug(canvas: Canvas) {
        val paint = debugPaint
        val x = valueX()
        val top = 0f
        val bottom = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = DEBUG_GRAB
        canvas.drawRect(x - thumbGrabHalf, top, x + thumbGrabHalf, bottom, paint)
        if (!detent.isNaN()) {
            val center = minX + (detent - range.start) / span * (maxX - minX)
            val band = DETENT_BAND / 2f * (maxX - minX)
            paint.color = DEBUG_DETENT
            canvas.drawRect(center - band, top, center + band, bottom, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f.dp(context)
        paint.color = DEBUG_EXCLUSION
        canvas.drawRect(exclusionRect, paint)
        paint.color = DEBUG_ANCHOR
        canvas.drawLine(x, top, x, bottom, paint)
        if (state == DRAGGING) {
            paint.color = DEBUG_FINGER
            canvas.drawLine(lastMoveX, top, lastMoveX, bottom, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        debugShown = DebugLayout.refresh()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus || DebugLayout.refresh() == debugShown) return
        debugShown = !debugShown
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) tracker.clear()
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                val x = valueX()
                state = if (abs(downX - x) <= thumbGrabHalf) DRAGGING else PENDING
                valueSpring.cancel()
                if (state == DRAGGING) {
                    grabOffset = x - downX
                    startDrag(downX, event.eventTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                when (state) {
                    PENDING -> {
                        val dx = abs(x - downX)
                        val dy = abs(event.getY(index) - downY)
                        if (dy > touchSlop && dy >= dx) {
                            state = IDLE
                            setInteracting(false)
                        } else if (dx > touchSlop && dx > dy) {
                            state = DRAGGING
                            grabOffset = 0f
                            startDrag(x, event.eventTime)
                            drag(x, event.eventTime)
                        }
                    }
                    DRAGGING -> drag(x, event.eventTime)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activePointerId) {
                    val next = if (index == 0) 1 else 0
                    activePointerId = event.getPointerId(next)
                    val x = event.getX(next)
                    downX = x
                    downY = event.getY(next)
                    grabOffset = valueX() - x
                    lastMoveX = x
                    lastMoveTime = event.eventTime
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state == PENDING) {
                    state = IDLE
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    performClick()
                    animateTo(valueAt(event.x))
                    if (!valueSpring.isRunning) setInteracting(false)
                } else if (state == DRAGGING) {
                    state = IDLE
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    tracker.computeCurrentVelocity(1000)
                    val flung = flingToDetent(tracker.getXVelocity(event.getPointerId(event.actionIndex)))
                    endDrag(closeInteraction = !flung)
                } else {
                    endGesture()
                }
            }
            MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return true
    }

    private fun endGesture() {
        val dragging = state == DRAGGING
        state = IDLE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        if (dragging) endDrag() else setInteracting(false)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (enabled || state == IDLE && !interacting) return
        endGesture()
        valueSpring.cancel()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidateShapes()
    }

    private fun startDrag(x: Float, time: Long) {
        parent?.requestDisallowInterceptTouchEvent(true)
        lastMoveX = x
        lastMoveTime = time
        speed = 0f
        lastHumStep = humStepOf(value)
        animatePress(1f)
        onPressedChanged?.invoke(true)
        setInteracting(true)
    }

    private fun drag(x: Float, time: Long) {
        val dt = time - lastMoveTime
        if (dt > 0) {
            val instant = abs(x - lastMoveX) * 1000f / dt
            speed += (instant - speed) * 0.5f
            lastMoveX = x
            lastMoveTime = time
        }
        applyUserValue(valueAt(x + grabOffset), haptics = true)
        if (DebugLayout.isShown) invalidate()
    }

    private fun endDrag(closeInteraction: Boolean = true) {
        animatePress(0f)
        onPressedChanged?.invoke(false)
        if (!closeInteraction) return
        setInteracting(false)
        updateExclusionRects()
    }

    private fun flingToDetent(velocity: Float): Boolean {
        if (detent.isNaN() || abs(velocity) <= flingMinVelocity || !animatorsEnabled) return false
        val cap = FLING_MAX_DISTANCE * (width - paddingLeft - paddingRight)
        val distance = (abs(velocity) / 1000f * DECELERATION_RATE / (1f - DECELERATION_RATE)).coerceAtMost(cap)
        val projected = range.start + ((valueX() + distance * sign(velocity) - minX) / (maxX - minX)) * span
        val crosses = if (velocity > 0) detent > value && detent < projected else detent < value && detent > projected
        if (!crosses) return false
        flingingToDetent = true
        springTo(detent, FLING_STIFFNESS, FLING_DAMPING)
        return true
    }

    private fun animatePress(target: Float) {
        if (animatorsEnabled) {
            pressSpring.animateTo(target)
            return
        }
        pressSpring.cancel()
        pressSpring.value = target
        press = target
        invalidateShapes()
    }

    private fun animateTo(target: Float) {
        if (target == value) return
        setInteracting(true)
        if (!animatorsEnabled) {
            applyUserValue(target, haptics = false)
            setInteracting(false)
            updateExclusionRects()
            return
        }
        springTo(target, SPRING_STIFFNESS, 1f)
    }

    private fun springTo(target: Float, stiffness: Float, dampingRatio: Float) {
        springTarget = target
        valueSpring.stiffness = stiffness
        valueSpring.dampingRatio = dampingRatio
        valueSpring.value = (value - range.start) / span
        valueSpring.animateTo((target - range.start) / span)
    }

    private fun setInteracting(active: Boolean) {
        if (interacting == active) return
        interacting = active
        onInteractionChanged?.invoke(active)
    }

    private fun applyUserValue(newValue: Float, haptics: Boolean) {
        val old = current
        if (newValue == old) return
        current = newValue
        invalidateShapes()
        if (haptics) feedback(old, newValue)
        onValueChanged?.invoke(newValue)
        announceValue()
    }

    private fun feedback(old: Float, new: Float) {
        val now = SystemClock.uptimeMillis()
        val enteredEnd = new == range.start && old != range.start || new == range.endInclusive && old != range.endInclusive
        if (enteredEnd || new == detent) {
            if (now - lastClickTime >= clickIntervalMs) {
                lastClickTime = now
                click.perform()
            }
            lastHumStep = humStepOf(new)
            return
        }
        if (now - lastClickTime >= HUM_QUIET_AFTER_CLICK_MS) humTick(new, now)
    }

    private fun humStepOf(value: Float) = (value * 100).roundToInt()

    private fun humTick(new: Float, now: Long) {
        val vibrator = vibrator
        val attributes = touchAttributes
        val usePrimitive = humPrimitiveSupported && vibrator != null && attributes != null
        if (!usePrimitive && Build.VERSION.SDK_INT < 34) return
        if (now - lastHumTime < HUM_INTERVAL_MS) return
        val step = humStepOf(new)
        if (step == lastHumStep) return
        lastHumStep = step
        lastHumTime = now
        if (Build.VERSION.SDK_INT >= 33 && usePrimitive) {
            val t = (speed / humFullSpeed).coerceIn(0f, 1f)
            vibrator.vibrate(humEffect((t * (HUM_BUCKETS - 1)).roundToInt()), attributes)
        } else if (Build.VERSION.SDK_INT >= 34) {
            performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }

    @RequiresApi(33)
    private fun humEffect(bucket: Int): VibrationEffect {
        humEffects[bucket]?.let { return it as VibrationEffect }
        val amplitude = HUM_MIN_AMPLITUDE + (HUM_MAX_AMPLITUDE - HUM_MIN_AMPLITUDE) * bucket / (HUM_BUCKETS - 1)
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, amplitude)
            .compose()
        humEffects[bucket] = effect
        return effect
    }

    private fun step(direction: Int): Boolean {
        val target = (value + direction * span * STEP).coerceIn(range)
        if (target == value) return false
        jumpTo(target)
        return true
    }

    private fun jumpTo(target: Float) {
        valueSpring.cancel()
        applyUserValue(target, haptics = false)
        updateExclusionRects()
    }

    private fun announceValue() {
        if (accessibility?.isEnabled != true) return
        val description = formatValue?.invoke(value) ?: "${((value - range.start) / span * 100).roundToInt()}%"
        if (description == announced) return
        announced = description
        if (Build.VERSION.SDK_INT >= 30) {
            stateDescription = description
        } else {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> step(-1) || super.onKeyDown(keyCode, event)
        KeyEvent.KEYCODE_DPAD_RIGHT -> step(1) || super.onKeyDown(keyCode, event)
        else -> super.onKeyDown(keyCode, event)
    }

    override fun getAccessibilityClassName(): CharSequence = SeekBar::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        @Suppress("DEPRECATION")
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, range.start, range.endInclusive, value,
        )
        if (Build.VERSION.SDK_INT >= 30) formatValue?.let { info.stateDescription = it(value) }
        if (!isEnabled) return
        if (value > range.start) info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (value < range.endInclusive) info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (Build.VERSION.SDK_INT >= 24) info.addAction(AccessibilityAction.ACTION_SET_PROGRESS)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) when {
            action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> return step(1)
            action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> return step(-1)
            Build.VERSION.SDK_INT >= 24 && action == android.R.id.accessibilityActionSetProgress && arguments != null -> {
                jumpTo(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).coerceIn(range))
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        endGesture()
        valueSpring.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        pressSpring.cancel()
        pressSpring.value = 0f
        press = 0f
        shapesDirty = true
    }

    private companion object {
        const val IDLE = 0
        const val PENDING = 1
        const val DRAGGING = 2
        const val STEP = 0.05f
        const val DETENT_BAND = 0.02f
        const val PRESSED_SCALE = 1.5f
        const val HOLE_ALPHA = 38
        const val HOLE_FADE = 0.35f
        const val FOCUS_ALPHA = 77
        const val DECELERATION_RATE = 0.998f
        const val FLING_MAX_DISTANCE = 0.12f
        const val FLING_STIFFNESS = 900f
        const val FLING_DAMPING = 0.85f
        const val SPRING_STIFFNESS = 600f
        const val PRESS_DAMPING = 0.6f
        const val HUM_BUCKETS = 8
        const val HUM_INTERVAL_MS = 50L
        const val HUM_QUIET_AFTER_CLICK_MS = 80L
        const val HUM_MIN_AMPLITUDE = 0.1f
        const val HUM_MAX_AMPLITUDE = 0.5f
        const val DEBUG_GRAB = 0x3334C759
        const val DEBUG_DETENT = 0x55FF9500
        const val DEBUG_EXCLUSION = 0xFF007AFF.toInt()
        const val DEBUG_ANCHOR = 0xFFFF3B30.toInt()
        const val DEBUG_FINGER = 0xFFAF52DE.toInt()
    }
}
