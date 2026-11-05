package vadiole.unicode.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

class PathRevealLayout(context: Context) : FrameLayout(context) {
    var onRevealChanged: ((Boolean) -> Unit)? = null
    private val retainedPath = Path()
    private val outsidePath = Path()
    private val edgeVeil = EdgeVeil(context)
    private val shadow = PathShadow(context)
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var revealing = false

    var vignetteStrength: Float
        get() = edgeVeil.strength
        set(value) {
            edgeVeil.strength = value.coerceIn(0f, 1f)
            invalidate()
        }

    var vignetteSize: Float
        get() = edgeVeil.sizeDp
        set(value) {
            edgeVeil.sizeDp = value
            invalidate()
        }

    fun reveal(path: Path) {
        retainedPath.set(path)
        outsidePath.set(path)
        outsidePath.toggleInverseFillType()
        shadow.update(path)
        if (!revealing) {
            revealing = true
            onRevealChanged?.invoke(true)
        }
        animateTo(1f)
    }

    fun restore() = animateTo(0f)

    fun reset() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        progress = 0f
        if (revealing) {
            revealing = false
            onRevealChanged?.invoke(false)
        }
        invalidate()
    }

    private fun animateTo(target: Float) {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        if (!animatorsEnabled || progress == target) {
            progress = target
            if (target == 0f) reset() else invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 160
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    if (target == 0f) reset()
                }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        edgeVeil.setSize(w, h)
    }

    override fun draw(canvas: Canvas) {
        if (progress == 0f) {
            super.draw(canvas)
            return
        }
        edgeVeil.draw(canvas, progress)
        if (progress < 1f) {
            val outside = canvas.save()
            canvas.clipPath(outsidePath)
            val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), ((1f - progress) * 255).toInt())
            super.draw(canvas)
            canvas.restoreToCount(layer)
            canvas.restoreToCount(outside)
        }
        val shadowClip = canvas.save()
        canvas.clipPath(outsidePath)
        shadow.draw(canvas, progress)
        canvas.restoreToCount(shadowClip)
        val inside = canvas.save()
        canvas.clipPath(retainedPath)
        super.draw(canvas)
        canvas.restoreToCount(inside)
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
}
