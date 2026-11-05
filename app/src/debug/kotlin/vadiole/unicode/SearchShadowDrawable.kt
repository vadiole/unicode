package vadiole.unicode

import android.content.Context
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

internal class SearchShadowDrawable(context: Context, texture: Int = R.drawable.prototype_search_shadow) : Drawable() {
    private val bitmap = (context.getDrawable(texture) as BitmapDrawable).bitmap
    private val scale = context.resources.displayMetrics.density / 4f
    private val half = bitmap.width / 2
    private val padding = (bitmap.width / 8f - 24f) * context.resources.displayMetrics.density
    private val source = Rect()
    private val target = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val middle = android.graphics.Bitmap.createBitmap(bitmap, half - 16, 0, 32, bitmap.height)
    private val shader = BitmapShader(middle, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val middlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@SearchShadowDrawable.shader }

    override fun onBoundsChange(bounds: Rect) {
        matrix.setScale(scale, scale)
        matrix.postTranslate(bounds.left.toFloat(), bounds.top - padding)
        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val centerLeft = bounds.left + bounds.height() / 2f
        val centerRight = bounds.right - bounds.height() / 2f
        val top = bounds.top - padding
        val bottom = bounds.bottom + padding
        source.set(0, 0, half, bitmap.height)
        target.set(bounds.left - padding, top, centerLeft, bottom)
        canvas.drawBitmap(bitmap, source, target, paint)
        if (centerRight > centerLeft) canvas.drawRect(centerLeft, top, centerRight, bottom, middlePaint)
        source.set(half, 0, bitmap.width, bitmap.height)
        target.set(centerRight, top, bounds.right + padding, bottom)
        canvas.drawBitmap(bitmap, source, target, paint)
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
