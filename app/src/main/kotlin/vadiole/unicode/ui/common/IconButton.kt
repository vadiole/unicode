package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.View
import vadiole.unicode.R

class IconButton(
    context: Context,
    icon: Drawable,
    private val iconSize: Int = 24.dp(context),
    private val size: Int = 48.dp(context),
    iconColor: Int = context.getColor(R.color.windowTextPrimary),
    private val floating: Boolean = true,
    altIcon: Drawable? = null,
) : View(context) {

    private val currentIcon: Drawable = icon.mutate().apply {
        setTint(iconColor)
    }
    private val altIcon: Drawable? = altIcon?.mutate()?.apply {
        setTint(iconColor)
    }
    private val surfaceDrawable = FloatingSurfaceDrawable(context, FloatingSurfaceDrawable.circle())
    private val rippleMask = ShapeDrawable(OvalShape())

    var iconProgress = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = size
        minimumHeight = size
        updateBackgroundInset()
        setOnTouchListener(TouchEffect(maxDragPx = 8f.dp(context)))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = size + paddingLeft + paddingRight
        val desiredHeight = size + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSizeAndState(desiredWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec, 0)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateIconBounds()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        updateBackgroundInset()
        updateIconBounds()
    }

    private fun updateBackgroundInset() {
        if (!floating) {
            val ripple = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.ripple)), null, null).apply {
                radius = 20.dp(context)
            }
            background = InsetDrawable(ripple, paddingLeft, paddingTop, paddingRight, paddingBottom)
            return
        }
        background = InsetDrawable(surfaceDrawable, paddingLeft, paddingTop, paddingRight, paddingBottom)
        val mask = InsetDrawable(rippleMask, paddingLeft, paddingTop, paddingRight, paddingBottom)
        foreground = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.ripple)), null, mask)
    }

    private fun updateIconBounds() {
        if (width <= 0 || height <= 0) return
        val left = paddingLeft + (width - paddingLeft - paddingRight - iconSize) / 2
        val top = (paddingTop + (height - paddingTop - paddingBottom - iconSize) / 2f).toInt()
        currentIcon.setBounds(left, top, left + iconSize, top + iconSize)
        altIcon?.setBounds(left, top, left + iconSize, top + iconSize)
        pivotX = left + iconSize / 2f
        pivotY = top + iconSize / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val alt = altIcon
        if (alt == null || iconProgress <= 0f) {
            currentIcon.alpha = 255
            currentIcon.draw(canvas)
            return
        }
        if (iconProgress >= 1f) {
            alt.alpha = 255
            alt.draw(canvas)
            return
        }
        drawSwapping(canvas, currentIcon, (iconProgress / SWAP_LENGTH).coerceIn(0f, 1f), out = true)
        drawSwapping(canvas, alt, ((iconProgress - SWAP_IN_START) / SWAP_LENGTH).coerceIn(0f, 1f), out = false)
    }

    private fun drawSwapping(canvas: Canvas, icon: Drawable, t: Float, out: Boolean) {
        val visible = if (out) 1f - t else t
        if (visible <= 0f) return
        val scale = SWAP_MIN_SCALE + (1f - SWAP_MIN_SCALE) * visible
        val rotation = if (out) SWAP_ROTATION * t else -SWAP_ROTATION * (1f - t)
        val count = canvas.save()
        canvas.rotate(rotation, pivotX, pivotY)
        canvas.scale(scale, scale, pivotX, pivotY)
        icon.alpha = (255 * visible).toInt()
        icon.draw(canvas)
        canvas.restoreToCount(count)
    }

    companion object {
        private const val SWAP_IN_START = 0.2f
        private const val SWAP_LENGTH = 0.5f
        private const val SWAP_MIN_SCALE = 0.5f
        private const val SWAP_ROTATION = 90f
    }
}
