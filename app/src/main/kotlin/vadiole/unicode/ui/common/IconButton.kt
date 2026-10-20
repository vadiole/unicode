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
    iconColor: Int = context.getColor(R.color.windowTextSecondary),
    private val floating: Boolean = true,
) : View(context) {

    private val currentIcon: Drawable = icon.mutate().apply {
        setTint(iconColor)
    }
    private val surfaceDrawable = FloatingSurfaceDrawable(context, FloatingSurfaceDrawable.circle())
    private val rippleMask = ShapeDrawable(OvalShape())

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
        if (!floating) return
        background = InsetDrawable(surfaceDrawable, paddingLeft, paddingTop, paddingRight, paddingBottom)
        val mask = InsetDrawable(rippleMask, paddingLeft, paddingTop, paddingRight, paddingBottom)
        foreground = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.ripple)), null, mask)
    }

    private fun updateIconBounds() {
        if (width <= 0 || height <= 0) return
        val left = paddingLeft + (width - paddingLeft - paddingRight - iconSize) / 2
        val top = (paddingTop + (height - paddingTop - paddingBottom - iconSize) / 2f).toInt()
        currentIcon.setBounds(left, top, left + iconSize, top + iconSize)
    }

    override fun onDraw(canvas: Canvas) {
        currentIcon.draw(canvas)
    }
}
