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
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
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

    var activeColor = context.getColor(R.color.primaryControl)
        set(value) {
            field = value
            invalidateShapes()
        }
    var trackHeight = 8f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var trackInnerRadius = 2f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var gap = 2f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var capsuleWidth = 32f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var capsuleHeight = 20f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var pressedScale = 1.4f
        set(value) {
            field = value
            invalidateShapes()
        }
    var borderWidth = 1.5f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var pressedFillAlpha = 0f
        set(value) {
            field = value
            invalidateShapes()
        }
    var lineWidth = 2f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var lineWidthPressed = 1f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var lineHeight = 10f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var linePressedInset = 3f.dp(context)
        set(value) {
            field = value
            invalidateShapes()
        }
    var lineColorChange = true
        set(value) {
            field = value
            invalidateShapes()
        }

    var pressStiffness = 600f
    var pressDamping = 0.6f
    var tapStiffness = 600f
    var tapToJump = true
    var jumpOnTrackDrag = true
    var slopMultiplier = 1f
    var detentBand = 0.03f
    var endClicks = true
    var detentClick = true
    var clickEffect = CLICK
        set(value) {
            field = value
            clickHaptic = null
        }
    var clickStrength = 0.7f
        set(value) {
            field = value
            clickHaptic = null
        }
    var hum = true
    var humPerStep = true
    var humStepPercent = 1
    var humQuietAfterClickMs = 80L
    var humIntervalMs = 50L
    var humMinAmplitude = 0.08f
        set(value) {
            field = value
            humEffects.fill(null)
        }
    var humMaxAmplitude = 0.15f
        set(value) {
            field = value
            humEffects.fill(null)
        }
    var humFullSpeed = 1500f.dp(context)
    var humSpeedCurve = 1f
    var humStep = 0.004f
    var humForceFallback = false

    private val span = range.endInclusive - range.start
    private val lineIdleColor = context.getColor(R.color.layer0)
    private val inactiveColor = context.getColor(R.color.windowDivider)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsulePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val activePath = Path()
    private val inactivePath = Path()
    private val capsulePath = Path()
    private val shapeRect = RectF()
    private val radii = FloatArray(8)
    private var shapesDirty = true
    private var lineX = 0f
    private var lineHalf = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val thumbTouchHalf = 22f.dp(context)
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

    private val clickIntervalMs = if (Build.VERSION.SDK_INT >= 34) 17L else 30L
    private var lastClickTime = 0L
    private var lastHumTime = 0L
    private var lastHumValue = 0f
    private var lastHumStep = 0
    private var clickHaptic: Any? = null
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
    private val pressProgress = FloatValueHolder(0f)
    private val pressSpring = SpringAnimation(pressProgress).apply {
        setMinimumVisibleChange(0.002f)
        spring = SpringForce(0f)
        addUpdateListener { _, value, _ ->
            press = value
            invalidateShapes()
        }
    }
    private val valueSpring = SpringAnimation(FloatValueHolder(0f)).apply {
        setMinimumVisibleChange(0.0005f)
        spring = SpringForce(0f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        addUpdateListener { _, fraction, _ -> applyUserValue(range.start + fraction * span, haptics = false) }
        addEndListener { _, _, _, _ ->
            if (state != DRAGGING) {
                setInteracting(false)
                updateExclusionRects()
            }
        }
    }

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
        return if (!detent.isNaN() && abs(raw - detent) <= detentBand * span / 2f) detent else raw
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!changed) return
        shapesDirty = true
        updateExclusionRects()
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
        val scale = 1f + (pressedScale - 1f) * press
        val cx = valueX()
        val cy = height / 2f
        val w = capsuleWidth * scale
        val h = capsuleHeight * scale
        val cut = (w / 2f + gap) * (1f - p)
        val outer = trackHeight / 2f
        val inner = trackInnerRadius * (1f - p)
        segment(activePath, paddingLeft.toFloat(), cx - cut, cy, outer, inner)
        segment(inactivePath, cx + cut, (width - paddingRight).toFloat(), cy, inner, outer)

        shapeRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        capsulePath.rewind()
        capsulePath.addSquircle(shapeRect, h / 2f)
        capsulePaint.color = withAlpha(activeColor, 1f + (pressedFillAlpha - 1f) * p)
        borderPaint.color = withAlpha(activeColor, p)
        borderPaint.strokeWidth = borderWidth

        val lineW = lineWidth + (lineWidthPressed - lineWidth) * p
        val lineH = lineHeight + (h - 2f * linePressedInset - lineHeight) * p
        linePaint.strokeWidth = lineW
        linePaint.color = if (lineColorChange) ColorUtils.blendARGB(lineIdleColor, activeColor, p) else lineIdleColor
        lineX = cx
        lineHalf = max(0f, (lineH - lineW) / 2f)
    }

    private fun segment(path: Path, left: Float, right: Float, cy: Float, leftRadius: Float, rightRadius: Float) {
        path.rewind()
        if (right <= left) return
        radii[0] = leftRadius
        radii[1] = leftRadius
        radii[2] = rightRadius
        radii[3] = rightRadius
        radii[4] = rightRadius
        radii[5] = rightRadius
        radii[6] = leftRadius
        radii[7] = leftRadius
        shapeRect.set(left, cy - trackHeight / 2f, right, cy + trackHeight / 2f)
        path.addRoundRect(shapeRect, radii, Path.Direction.CW)
    }

    private fun withAlpha(color: Int, alpha: Float) =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt())

    override fun onDraw(canvas: Canvas) {
        if (shapesDirty) layoutShapes()
        trackPaint.color = activeColor
        canvas.drawPath(activePath, trackPaint)
        trackPaint.color = inactiveColor
        canvas.drawPath(inactivePath, trackPaint)
        if (capsulePaint.alpha > 0) canvas.drawPath(capsulePath, capsulePaint)
        if (borderPaint.alpha > 0 && borderWidth > 0f) canvas.drawPath(capsulePath, borderPaint)
        val cy = height / 2f
        canvas.drawLine(lineX, cy - lineHalf, lineX, cy + lineHalf, linePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                val x = valueX()
                if (abs(downX - x) <= max(capsuleWidth / 2f, thumbTouchHalf)) {
                    state = DRAGGING
                    valueSpring.cancel()
                    grabOffset = x - downX
                    startDrag(downX, event.eventTime)
                } else {
                    state = PENDING
                    valueSpring.cancel()
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
                        } else if (dx > touchSlop * slopMultiplier && dx > dy) {
                            state = DRAGGING
                            grabOffset = if (jumpOnTrackDrag) 0f else valueX() - x
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
                if (state == PENDING && tapToJump) {
                    performClick()
                    animateTo(valueAt(event.x))
                } else if (state == DRAGGING) {
                    endDrag()
                }
                state = IDLE
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_CANCEL -> {
                if (state == DRAGGING) endDrag()
                state = IDLE
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun startDrag(x: Float, time: Long) {
        parent?.requestDisallowInterceptTouchEvent(true)
        lastMoveX = x
        lastMoveTime = time
        speed = 0f
        lastHumValue = value
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
    }

    private fun endDrag() {
        animatePress(0f)
        onPressedChanged?.invoke(false)
        setInteracting(false)
        updateExclusionRects()
    }

    private fun animatePress(target: Float) {
        if (!animatorsEnabled) {
            pressSpring.cancel()
            pressProgress.value = target
            press = target
            invalidateShapes()
            return
        }
        pressSpring.spring.setStiffness(pressStiffness).setDampingRatio(pressDamping)
        pressSpring.animateToFinalPosition(target)
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
        valueSpring.spring.setStiffness(tapStiffness)
        valueSpring.setStartValue((value - range.start) / span)
        valueSpring.animateToFinalPosition((target - range.start) / span)
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
        val enteredEnd = endClicks &&
            (new == range.start && old != range.start || new == range.endInclusive && old != range.endInclusive)
        val enteredDetent = detentClick && new == detent
        if (enteredEnd || enteredDetent) {
            if (now - lastClickTime >= clickIntervalMs) {
                lastClickTime = now
                performClickHaptic()
            }
            lastHumValue = new
            lastHumStep = humStepOf(new)
            return
        }
        if (hum && new != detent && now - lastClickTime >= humQuietAfterClickMs) humTick(new, now)
    }

    private fun humStepOf(value: Float) = Math.floorDiv((value * 100).roundToInt(), humStepPercent.coerceAtLeast(1))

    private fun performClickHaptic() {
        val vibrator = vibrator
        val attributes = touchAttributes
        if (Build.VERSION.SDK_INT >= 33 && clickEffect != THRESHOLD && vibrator != null && attributes != null) {
            val primitive = when (clickEffect) {
                LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                else -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            if (vibrator.areAllPrimitivesSupported(primitive)) {
                val effect = clickHaptic as? VibrationEffect ?: VibrationEffect.startComposition()
                    .addPrimitive(primitive, clickStrength.coerceIn(0f, 1f))
                    .compose()
                    .also { clickHaptic = it }
                vibrator.vibrate(effect, attributes)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= 34) performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
    }

    private fun humTick(new: Float, now: Long) {
        val vibrator = vibrator
        val fallbackAvailable = Build.VERSION.SDK_INT >= 34
        val usePrimitive = humPrimitiveSupported && vibrator != null && !(humForceFallback && fallbackAvailable)
        if (!usePrimitive && !fallbackAvailable) return
        if (now - lastHumTime < humIntervalMs) return
        if (humPerStep) {
            val step = humStepOf(new)
            if (step == lastHumStep) return
            lastHumStep = step
        } else {
            if (abs(new - lastHumValue) < humStep * span) return
            lastHumValue = new
        }
        lastHumTime = now
        val attributes = touchAttributes
        if (Build.VERSION.SDK_INT >= 33 && usePrimitive && attributes != null) {
            val t = (speed / humFullSpeed).coerceIn(0f, 1f).pow(humSpeedCurve)
            vibrator.vibrate(humEffect((t * (HUM_BUCKETS - 1)).roundToInt()), attributes)
        } else if (Build.VERSION.SDK_INT >= 34) {
            performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    }

    @RequiresApi(33)
    private fun humEffect(bucket: Int): VibrationEffect {
        humEffects[bucket]?.let { return it as VibrationEffect }
        val amplitude = (humMinAmplitude + (humMaxAmplitude - humMinAmplitude) * bucket / (HUM_BUCKETS - 1)).coerceIn(0f, 1f)
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, amplitude)
            .compose()
        humEffects[bucket] = effect
        return effect
    }

    private fun step(direction: Int): Boolean {
        val target = (value + direction * span * STEP).coerceIn(range)
        if (target == value) return false
        valueSpring.cancel()
        applyUserValue(target, haptics = false)
        updateExclusionRects()
        return true
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
                valueSpring.cancel()
                applyUserValue(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).coerceIn(range), haptics = false)
                updateExclusionRects()
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (state == DRAGGING) endDrag()
        state = IDLE
        valueSpring.cancel()
        pressSpring.cancel()
        pressProgress.value = 0f
        press = 0f
        shapesDirty = true
    }

    companion object {
        private const val IDLE = 0
        private const val PENDING = 1
        private const val DRAGGING = 2
        private const val STEP = 0.05f
        private const val HUM_BUCKETS = 8
        const val CLICK = 0
        const val LOW_TICK = 1
        const val THRESHOLD = 2
        const val TICK = 3
    }
}
