package vadiole.unicode.ui.table.selector

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.animation.addListener
import androidx.core.graphics.withClip
import vadiole.unicode.ui.common.FloatingSurfaceDrawable
import vadiole.unicode.ui.common.dp

internal object BlockSelectorGeometry {
    const val CORNER_RADIUS_DP = 16
    const val FLOATING_INSET_DP = 8f
    const val CONTENT_VERTICAL_PADDING_DP = 8
    const val ARROW_WIDTH_DP = 24f
    const val ARROW_HEIGHT_DP = 11f
    const val ARROW_OVERLAP_DP = 4f
    const val ARROW_TIP_RADIUS_DP = 2f
}

internal class BlockSelectorSurfaceView(
    context: Context,
    private val listView: BlockSelectorView,
) : FrameLayout(context) {

    private val floatingInset = BlockSelectorGeometry.FLOATING_INSET_DP.dp(context)
    private val arrowWidth = BlockSelectorGeometry.ARROW_WIDTH_DP.dp(context)
    private val arrowHeight = BlockSelectorGeometry.ARROW_HEIGHT_DP.dp(context)
    private val arrowOverlap = BlockSelectorGeometry.ARROW_OVERLAP_DP.dp(context)
    private val topMargin = (arrowHeight - arrowOverlap).toInt()
    var anchorX = Float.NaN
    private val surfaceDrawable = FloatingSurfaceDrawable(
        context = context,
        pathProvider = FloatingSurfaceDrawable.squircleWithTopArrow(
            cornerRadius = BlockSelectorGeometry.CORNER_RADIUS_DP.dp(context),
            arrowWidth = arrowWidth,
            arrowHeight = arrowHeight,
            arrowOverlap = arrowOverlap,
            arrowTipRadius = BlockSelectorGeometry.ARROW_TIP_RADIUS_DP.dp(context),
            arrowCenterX = { anchorX },
        ),
    )
    private val clipPath = Path()
    private var showAnimator: ValueAnimator? = null
    private var showProgress = 0f
    private var isDismissAnimation = false

    init {
        clipChildren = false
        clipToPadding = false
        background = surfaceDrawable
        alpha = 0f
        addView(listView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    surfaceDrawable.getOutline(outline)
                }
            }
            listView.clipToOutline = true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setShowProgress(showProgress, isDismissAnimation)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.dispatchDraw(canvas)
            return
        }

        if (!surfaceDrawable.copySurfacePath(clipPath)) return
        canvas.withClip(clipPath) {
            super.dispatchDraw(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        cancelShowAnimator()
        super.onDetachedFromWindow()
    }

    fun show() {
        cancelShowAnimator()
        setShowProgress(showProgress, dismiss = false)

        val animator = ValueAnimator.ofFloat(showProgress, 1f)
        showAnimator = animator
        animator.apply {
            duration = 300
            interpolator = showInterpolator
            addUpdateListener { animator ->
                setShowProgress(animator.animatedValue as Float, dismiss = false)
            }
            addListener(onEnd = {
                if (showAnimator === animator) showAnimator = null
            })
            start()
        }
    }

    fun dismiss(onEnd: () -> Unit) {
        cancelShowAnimator()
        setShowProgress(showProgress, dismiss = true)

        val animator = ValueAnimator.ofFloat(showProgress, 0f)
        showAnimator = animator
        animator.apply {
            duration = 300
            interpolator = showInterpolator
            addUpdateListener { animator ->
                setShowProgress(animator.animatedValue as Float, dismiss = true)
            }
            addListener(onEnd = {
                if (showAnimator === animator) {
                    showAnimator = null
                    onEnd()
                }
            })
            start()
        }
    }

    private fun cancelShowAnimator() {
        showAnimator?.let { animator ->
            animator.removeAllListeners()
            animator.removeAllUpdateListeners()
            animator.cancel()
        }
        showAnimator = null
    }

    private fun setShowProgress(progress: Float, dismiss: Boolean) {
        showProgress = progress
        isDismissAnimation = dismiss

        val actualProgress = 1f - progress
        alpha = progress
        translationY = -8.dp(context).toFloat() * actualProgress

        if (width <= 0 || height <= 0) {
            surfaceDrawable.setBounds(0, 0, 0, 0)
            invalidateClipOutline()
            invalidate()
            return
        }

        val clipModifier = if (dismiss) 0.1f else 1f
        val originX = if (anchorX.isNaN()) width / 2f else anchorX.coerceIn(floatingInset, width - floatingInset)
        val horizontalShrink = 0.8f * actualProgress * clipModifier
        val topOffset = 12.dp(context).toFloat() * actualProgress
        val bottomOffset = height * 0.9f * actualProgress * clipModifier
        val left = floatingInset + (originX - floatingInset) * horizontalShrink
        val right = width - floatingInset - (width - floatingInset - originX) * horizontalShrink
        val top = topMargin + floatingInset + topOffset
        val bottom = height - floatingInset - bottomOffset

        surfaceDrawable.setBounds(
            left.toInt(),
            top.toInt(),
            right.toInt(),
            bottom.toInt()
        )
        invalidateClipOutline()
        invalidate()
    }

    private fun invalidateClipOutline() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listView.invalidateOutline()
        }
    }

    companion object {
        private val showInterpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }
}
