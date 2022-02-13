package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class SpacerDrawable(val height: Int = 0, val width: Int = 0) : Drawable() {
    override fun getIntrinsicHeight(): Int {
        return height
    }

    override fun getIntrinsicWidth(): Int {
        return width
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT
    override fun setAlpha(alpha: Int) = Unit
    override fun draw(canvas: Canvas) = Unit
}