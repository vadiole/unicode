package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect

/**
 * StateListDrawable with iOS-like rounded corners
 *
 * [Read more about Squircle](https://www.figma.com/blog/desperately-seeking-squircles)
 */
class SquircleDrawable(
    cornerRadius: Int,
    topLeftRadius: Int = cornerRadius,
    topRightRadius: Int = cornerRadius,
    bottomRightRadius: Int = cornerRadius,
    bottomLeftRadius: Int = cornerRadius,
) : StateColorDrawable() {

    private val squircle = Squircle4(topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius)

    override fun isStateful(): Boolean = true

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun onBoundsChange(bounds: Rect) {
        squircle.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(squircle.path, paint)
    }
}