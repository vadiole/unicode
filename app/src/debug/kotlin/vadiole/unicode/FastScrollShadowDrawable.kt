package vadiole.unicode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

internal class FastScrollShadowDrawable(context: Context) : Drawable() {
    private val bitmap = (context.getDrawable(R.drawable.prototype_scroller_shadow) as BitmapDrawable).bitmap
    private val scale = context.resources.displayMetrics.density / 4f
    private val padding = 12f * context.resources.displayMetrics.density
    private val half = bitmap.height / 2
    private val source = Rect()
    private val top = RectF()
    private val center = RectF()
    private val bottom = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val middle = Bitmap.createBitmap(bitmap, 0, half - 4, bitmap.width, 8)
    private val shader = BitmapShader(middle, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
    private val matrix = Matrix()
    private val middlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@FastScrollShadowDrawable.shader }

    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left - padding
        val right = bounds.right + padding
        val centerTop = bounds.top + bounds.width() / 2f
        val centerBottom = bounds.bottom - bounds.width() / 2f
        top.set(left, bounds.top - padding, right, centerTop)
        center.set(left, centerTop, right, centerBottom)
        bottom.set(left, centerBottom, right, bounds.bottom + padding)
        matrix.setScale(scale, scale)
        matrix.postTranslate(left, centerTop)
        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        source.set(0, 0, bitmap.width, half)
        canvas.drawBitmap(bitmap, source, top, paint)
        canvas.drawRect(center, middlePaint)
        source.set(0, half, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, source, bottom, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        middlePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        middlePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
