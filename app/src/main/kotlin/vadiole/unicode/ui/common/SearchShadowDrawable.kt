package vadiole.unicode.ui.common

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
import vadiole.unicode.R

class SearchShadowDrawable(context: Context) : Drawable() {
    private val bitmap = (context.getDrawable(R.drawable.search_shadow) as BitmapDrawable).bitmap
    private val scale = context.resources.displayMetrics.density / 4f
    private val padding = (bitmap.width / 8f - 24f) * context.resources.displayMetrics.density
    private val leftSource = Rect(0, 0, bitmap.width / 2, bitmap.height)
    private val rightSource = Rect(bitmap.width / 2, 0, bitmap.width, bitmap.height)
    private val leftTarget = RectF()
    private val rightTarget = RectF()
    private val centerTarget = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val middle = Bitmap.createBitmap(bitmap, bitmap.width / 2 - 16, 0, 32, bitmap.height)
    private val shader = BitmapShader(middle, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val middlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = this@SearchShadowDrawable.shader }

    override fun onBoundsChange(bounds: Rect) {
        val centerLeft = bounds.left + bounds.height() / 2f
        val centerRight = bounds.right - bounds.height() / 2f
        val top = bounds.top - padding
        val bottom = bounds.bottom + padding
        leftTarget.set(bounds.left - padding, top, centerLeft, bottom)
        rightTarget.set(centerRight, top, bounds.right + padding, bottom)
        centerTarget.set(centerLeft, top, centerRight, bottom)
        matrix.setScale(scale, scale)
        matrix.postTranslate(bounds.left.toFloat(), top)
        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.drawBitmap(bitmap, leftSource, leftTarget, paint)
        if (!centerTarget.isEmpty) canvas.drawRect(centerTarget, middlePaint)
        canvas.drawBitmap(bitmap, rightSource, rightTarget, paint)
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
