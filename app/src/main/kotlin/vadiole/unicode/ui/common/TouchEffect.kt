package vadiole.unicode.ui.common

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.sign

class TouchEffect(
    private val maxDragPx: Float,
    private val pressedScale: Float = 1.16f,
) : View.OnTouchListener {

    private var initialX = 0f
    private var initialY = 0f
    private var dragging = false
    private var touchSlop = 0
    private var velocityTracker: VelocityTracker? = null

    private var springTx: SpringAnimation? = null
    private var springTy: SpringAnimation? = null
    private var springScaleX: SpringAnimation? = null
    private var springScaleY: SpringAnimation? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (touchSlop == 0) {
            touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        }
        ensureSprings(view)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(view, event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> onCancel()
        }
        return false
    }

    private fun ensureSprings(view: View) {
        if (springTx != null) return
        springTx = SpringAnimation(view, DynamicAnimation.TRANSLATION_X).apply {
            setMinValue(-maxDragPx)
            setMaxValue(maxDragPx)
            spring = SpringForce().apply {
                stiffness = POSITION_STIFFNESS
                dampingRatio = POSITION_DAMPING_RATIO
            }
        }
        springTy = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).apply {
            setMinValue(-maxDragPx)
            setMaxValue(maxDragPx)
            spring = SpringForce().apply {
                stiffness = POSITION_STIFFNESS
                dampingRatio = POSITION_DAMPING_RATIO
            }
        }
        springScaleX = SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce().apply {
                stiffness = SCALE_STIFFNESS
                dampingRatio = SCALE_DAMPING_RATIO
            }
        }
        springScaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce().apply {
                stiffness = SCALE_STIFFNESS
                dampingRatio = SCALE_DAMPING_RATIO
            }
        }
    }

    private fun onDown(event: MotionEvent) {
        springTx?.cancel()
        springTy?.cancel()

        initialX = event.rawX
        initialY = event.rawY
        dragging = false

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain()
        addRawMovement(event)

        springScaleX?.animateToFinalPosition(pressedScale)
        springScaleY?.animateToFinalPosition(pressedScale)
    }

    private fun onMove(view: View, event: MotionEvent) {
        addRawMovement(event)

        val dx = event.rawX - initialX
        val dy = event.rawY - initialY

        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
            dragging = true
        }

        if (dragging) {
            view.translationX = rubberBand(dx, maxDragPx)
            view.translationY = rubberBand(dy, maxDragPx)
        }
    }

    private fun onUp(event: MotionEvent) {
        addRawMovement(event)
        velocityTracker?.computeCurrentVelocity(1000)

        val dx = event.rawX - initialX
        val dy = event.rawY - initialY
        val maxReleaseVelocity = maxDragPx * MAX_RELEASE_VELOCITY_FACTOR
        val velocityX = if (dragging) {
            rubberBandVelocity(velocityTracker?.xVelocity ?: 0f, dx, maxDragPx)
                .coerceIn(-maxReleaseVelocity, maxReleaseVelocity)
        } else {
            0f
        }
        val velocityY = if (dragging) {
            rubberBandVelocity(velocityTracker?.yVelocity ?: 0f, dy, maxDragPx)
                .coerceIn(-maxReleaseVelocity, maxReleaseVelocity)
        } else {
            0f
        }

        springTx?.setStartVelocity(velocityX)?.animateToFinalPosition(0f)
        springTy?.setStartVelocity(velocityY)?.animateToFinalPosition(0f)
        springScaleX?.animateToFinalPosition(1f)
        springScaleY?.animateToFinalPosition(1f)

        releaseVelocityTracker()
    }

    private fun onCancel() {
        springTx?.setStartVelocity(0f)?.animateToFinalPosition(0f)
        springTy?.setStartVelocity(0f)?.animateToFinalPosition(0f)
        springScaleX?.animateToFinalPosition(1f)
        springScaleY?.animateToFinalPosition(1f)
        releaseVelocityTracker()
    }

    private fun addRawMovement(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val offsetX = event.rawX - event.x
        val offsetY = event.rawY - event.y
        event.offsetLocation(offsetX, offsetY)
        try {
            tracker.addMovement(event)
        } finally {
            event.offsetLocation(-offsetX, -offsetY)
        }
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        private const val POSITION_STIFFNESS = 1500f
        private const val POSITION_DAMPING_RATIO = 0.65f
        private const val SCALE_STIFFNESS = 2500f
        private const val SCALE_DAMPING_RATIO = 0.55f
        private const val RUBBER_BAND_CONSTANT = 0.15f
        private const val MAX_RELEASE_VELOCITY_FACTOR = 40f

        private fun rubberBand(offset: Float, dimension: Float): Float {
            if (dimension <= 0f) return 0f
            val absOffset = abs(offset)
            if (absOffset < 0.01f) return 0f
            val dampened = (1f - 1f / (absOffset * RUBBER_BAND_CONSTANT / dimension + 1f)) * dimension
            return sign(offset) * dampened
        }

        private fun rubberBandVelocity(velocity: Float, offset: Float, dimension: Float): Float {
            if (dimension <= 0f) return 0f
            val denominator = abs(offset) * RUBBER_BAND_CONSTANT / dimension + 1f
            return velocity * RUBBER_BAND_CONSTANT / (denominator * denominator)
        }
    }
}
