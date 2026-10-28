package vadiole.unicode.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import vadiole.unicode.R
import vadiole.unicode.ui.common.Screen

class Nav(private val container: FrameLayout) {

    private val stack = ArrayList<Screen>()

    private var transition: ValueAnimator? = null

    private var backProgress = -1f

    private var backFromLeft = true

    private var backStartY = 0f

    private var scrim: ColorDrawable? = null

    private var scrimView: View? = null

    private var leaving: Screen? = null

    private var cachedCornerRadius = -1f

    private val emphasized = PathInterpolator(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    })

    private val gestureCurve = PathInterpolator(0.1f, 0.1f, 0f, 1f)

    private val settleCurve = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private val verticalCurve = DecelerateInterpolator()

    private val linear = LinearInterpolator()

    private val cornerClip = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val scale = view.scaleX.coerceAtLeast(0.01f)
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius() / scale)
        }
    }

    private fun fadeGate(t: Long): Float = when {
        t < 50L -> 0f
        t <= 133L -> (t - 50f) / 83f
        else -> 1f
    }

    fun setRoot(screen: Screen) {
        container.removeAllViews()
        stack.clear()
        stack.add(screen)
        container.addView(screen, matchParent())
    }

    fun push(screen: Screen) {
        clearBackState()
        val exiting = leaving
        if (exiting != null) {
            transition?.cancel()
            leaving = null
            pushFromExit(screen, exiting)
            return
        }
        val previous = stack.lastOrNull()
        stack.add(screen)
        container.addView(screen, matchParent())

        transition?.end()
        val offset = offset()
        screen.alpha = 0f
        screen.translationX = offset
        previous?.translationX = 0f
        previous?.alpha = 1f

        runTransition(
            onUpdate = { f ->
                val t = (f * DURATION).toLong()
                screen.translationX = offset * (1f - f)
                screen.alpha = fadeGate(t)
                previous?.translationX = -offset * f
            },
            onEnd = {
                screen.translationX = 0f
                screen.alpha = 1f
                previous?.let { if (it !in stack) container.removeView(it) else it.visibility = View.INVISIBLE }
            },
        )
    }

    private fun pushFromExit(screen: Screen, exiting: Screen) {
        val previous = stack.last()
        stack.add(screen)
        container.addView(screen, matchParent())
        screen.translationX = exiting.translationX
        screen.translationY = exiting.translationY
        screen.alpha = exiting.alpha
        screen.scaleX = exiting.scaleX
        screen.scaleY = exiting.scaleY
        if (exiting.clipToOutline) {
            screen.outlineProvider = cornerClip
            screen.clipToOutline = true
        }
        container.removeView(exiting)
        resetScreen(exiting)

        val fromTx = screen.translationX
        val fromTy = screen.translationY
        val fromAlpha = screen.alpha
        val fromScale = screen.scaleX
        val prevFromTx = previous.translationX
        val prevFromTy = previous.translationY
        val prevFromScale = previous.scaleX
        val offset = offset()
        val dim = scrim
        val dimFrom = dim?.alpha ?: 0

        runTransition(
            onUpdate = { f ->
                val scale = fromScale + (1f - fromScale) * f
                screen.scaleX = scale
                screen.scaleY = scale
                screen.invalidateOutline()
                screen.translationX = fromTx * (1f - f)
                screen.translationY = fromTy * (1f - f)
                screen.alpha = fromAlpha + (1f - fromAlpha) * f
                val prevScale = prevFromScale + (1f - prevFromScale) * f
                previous.scaleX = prevScale
                previous.scaleY = prevScale
                previous.translationX = prevFromTx + (-offset - prevFromTx) * f
                previous.translationY = prevFromTy * (1f - f)
                dim?.alpha = (dimFrom * (1f - f)).toInt()
            },
            onEnd = {
                resetScreen(screen)
                resetScreen(previous)
                if (previous in stack) previous.visibility = View.INVISIBLE
                removeScrim()
            },
        )
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        if (backProgress >= 0f) {
            commitBack()
            return true
        }
        transition?.end()
        val top = stack.removeAt(stack.lastIndex)
        val previous = stack.last()
        if (previous.parent == null) {
            container.addView(previous, 0, matchParent())
        }
        previous.visibility = View.VISIBLE

        val offset = offset()
        top.translationX = 0f
        top.alpha = 1f
        leaving = top
        previous.translationX = -offset
        previous.alpha = 1f

        runTransition(
            onUpdate = { f ->
                top.translationX = offset * f
                top.alpha = 1f - f
                previous.translationX = -offset * (1f - f)
            },
            onEnd = {
                previous.translationX = 0f
                previous.alpha = 1f
                if (top !in stack) container.removeView(top)
                resetScreen(top)
                leaving = null
            },
        )
        return true
    }

    fun startBack(fromLeft: Boolean = true, touchY: Float = 0f) {
        if (stack.size <= 1) return
        transition?.end()
        clearBackState()
        val top = stack.last()
        val previous = stack[stack.size - 2]
        if (previous.parent == null) {
            container.addView(previous, 0, matchParent())
        }
        previous.visibility = View.VISIBLE
        previous.alpha = 1f
        top.translationX = 0f
        top.alpha = 1f
        top.outlineProvider = cornerClip
        top.clipToOutline = true
        top.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        previous.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val dim = ColorDrawable(container.context.getColor(R.color.dialogDim)).apply { alpha = SCRIM_ALPHA }
        scrim = dim
        val dimView = View(container.context).apply { background = dim }
        scrimView = dimView
        container.addView(dimView, container.indexOfChild(top), matchParent())
        backFromLeft = fromLeft
        backStartY = touchY
        seekBack(0f, touchY)
    }

    fun progressBack(progress: Float, touchY: Float) {
        if (backProgress < 0f) return
        seekBack(progress, touchY)
    }

    fun cancelBack() {
        if (backProgress < 0f) return
        backProgress = -1f
        val top = stack.last()
        val previous = stack[stack.size - 2]
        val fromScale = top.scaleX
        val fromTx = top.translationX
        val fromTy = top.translationY

        runTransition(
            duration = CANCEL_DURATION,
            interpolator = settleCurve,
            onUpdate = { f ->
                val scale = fromScale + (1f - fromScale) * f
                top.scaleX = scale
                top.scaleY = scale
                top.invalidateOutline()
                top.translationX = fromTx * (1f - f)
                top.translationY = fromTy * (1f - f)
                previous.scaleX = scale
                previous.scaleY = scale
                previous.translationY = fromTy * (1f - f)
            },
            onEnd = {
                clearBackVisuals(top, previous)
                previous.visibility = View.INVISIBLE
            },
        )
    }

    private fun commitBack() {
        backProgress = -1f
        val top = stack.removeAt(stack.lastIndex)
        val previous = stack.last()
        leaving = top
        val fromScale = top.scaleX
        val fromTopTx = top.translationX
        val fromTy = top.translationY
        val fromTx = previous.translationX
        val offset = offset()

        runTransition(
            interpolator = linear,
            onUpdate = { f ->
                val e = emphasized.getInterpolation(f)
                val scale = fromScale + (1f - fromScale) * e
                top.scaleX = scale
                top.scaleY = scale
                top.invalidateOutline()
                top.translationX = fromTopTx + offset * e
                top.translationY = fromTy * (1f - e)
                top.alpha = (1f - f / FADE_OUT).coerceIn(0f, 1f)
                previous.scaleX = scale
                previous.scaleY = scale
                previous.translationX = fromTx * (1f - e)
                previous.translationY = fromTy * (1f - e)
                scrim?.alpha = (SCRIM_ALPHA * (1f - f / SCRIM_FADE)).toInt().coerceAtLeast(0)
            },
            onEnd = {
                clearBackVisuals(top, previous)
                if (top !in stack) container.removeView(top)
                leaving = null
            },
        )
    }

    private fun seekBack(progress: Float, touchY: Float) {
        backProgress = progress
        val p = gestureCurve.getInterpolation(progress)
        val scale = 1f - (1f - MIN_SCALE) * p
        val top = stack.last()
        val previous = stack[stack.size - 2]
        val w = container.width.toFloat()
        val h = container.height.toFloat()
        val margin = EDGE_MARGIN * container.resources.displayMetrics.density
        val maxDx = ((w - scale * w) / 2f - margin).coerceAtLeast(0f)
        val deltaY = touchY - backStartY
        val dyCap = ((h - scale * h) / 2f - margin).coerceAtLeast(0f)
        val ySign = if (deltaY >= 0f) 1f else -1f
        val yNorm = (abs(deltaY) / (h / 2f)).coerceIn(0f, 1f)
        val ty = ySign * dyCap * verticalCurve.getInterpolation(yNorm)
        top.scaleX = scale
        top.scaleY = scale
        top.invalidateOutline()
        top.translationX = if (backFromLeft) maxDx * p else 0f
        top.translationY = ty
        previous.scaleX = scale
        previous.scaleY = scale
        previous.translationX = -offset()
        previous.translationY = ty
    }

    private fun clearBackState() {
        if (backProgress < 0f) return
        backProgress = -1f
        clearBackVisuals(stack.last(), stack[stack.size - 2])
    }

    private fun clearBackVisuals(top: Screen, previous: Screen) {
        resetScreen(top)
        resetScreen(previous)
        removeScrim()
    }

    private fun removeScrim() {
        scrimView?.let { container.removeView(it) }
        scrimView = null
        scrim = null
    }

    private fun resetScreen(screen: Screen) {
        screen.scaleX = 1f
        screen.scaleY = 1f
        screen.translationX = 0f
        screen.translationY = 0f
        screen.alpha = 1f
        screen.clipToOutline = false
        screen.outlineProvider = ViewOutlineProvider.BACKGROUND
        screen.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun cornerRadius(): Float {
        var radius = cachedCornerRadius
        if (radius < 0f) {
            val insets = container.rootWindowInsets
            val systemRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
            } else {
                null
            }
            radius = systemRadius ?: (24f * container.resources.displayMetrics.density)
            if (insets != null) cachedCornerRadius = radius
        }
        return radius
    }

    fun current(): Screen? = stack.lastOrNull()

    fun canPop(): Boolean = stack.size > 1

    private fun offset(): Float = 96f * container.resources.displayMetrics.density

    private fun runTransition(
        duration: Long = DURATION,
        interpolator: TimeInterpolator = emphasized,
        onUpdate: (Float) -> Unit,
        onEnd: () -> Unit,
    ) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = duration
        anim.interpolator = interpolator
        anim.addUpdateListener { onUpdate(it.animatedValue as Float) }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
                transition = null
            }
        })
        transition = anim
        anim.start()
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private companion object {
        const val DURATION = 450L
        const val CANCEL_DURATION = 200L
        const val MIN_SCALE = 0.9f
        const val EDGE_MARGIN = 16f
        const val SCRIM_ALPHA = 77
        const val FADE_OUT = 0.2f
        const val SCRIM_FADE = 0.5f
    }
}
