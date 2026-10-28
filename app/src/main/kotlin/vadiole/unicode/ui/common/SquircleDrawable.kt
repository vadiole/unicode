package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF

class SquircleDrawable(
    cornerRadius: Int,
    topLeftRadius: Int = cornerRadius,
    topRightRadius: Int = cornerRadius,
    bottomRightRadius: Int = cornerRadius,
    bottomLeftRadius: Int = cornerRadius,
) : StateColorDrawable() {

    private val radii = floatArrayOf(
        topLeftRadius.toFloat(),
        topRightRadius.toFloat(),
        bottomRightRadius.toFloat(),
        bottomLeftRadius.toFloat(),
    )
    private val rect = RectF()
    private val path = Path()

    override fun isStateful(): Boolean = true

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        path.rewind()
        path.addSquircle(rect, radii)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}
