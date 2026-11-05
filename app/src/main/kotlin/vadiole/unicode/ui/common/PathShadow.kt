package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

class PathShadow(context: Context) {
    private val ambientBlur = 36f.dp(context)
    private val ambientOffset = 10f.dp(context)
    private val contactBlur = 5f.dp(context)
    private val contactOffset = 2f.dp(context)
    private val shape = Path()
    private val difference = Path()
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var left = 0f
    private var top = 0f

    fun update(path: Path) {
        difference.set(shape)
        if (bitmap != null && difference.op(path, Path.Op.XOR) && difference.isEmpty) return
        shape.set(path)
        if (shape.isEmpty) {
            bitmap = null
            return
        }
        shape.computeBounds(bounds, true)
        val padding = ceil(ambientBlur * 3f + ambientOffset)
        left = floor(bounds.left - padding)
        top = floor(bounds.top - padding)
        val width = ceil(bounds.right + padding - left).toInt()
        val height = ceil(bounds.bottom + padding - top).toInt()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
        }
        val canvas = Canvas(image)
        canvas.translate(-left, -top)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            setShadowLayer(ambientBlur, 0f, ambientOffset, Color.argb(17, 0, 0, 0))
        }
        canvas.drawPath(shape, shadowPaint)
        shadowPaint.setShadowLayer(contactBlur, 0f, contactOffset, Color.argb(20, 0, 0, 0))
        canvas.drawPath(shape, shadowPaint)
        bitmap = image
    }

    fun draw(canvas: Canvas, opacity: Float) {
        val image = bitmap ?: return
        paint.alpha = (opacity * 255).toInt()
        canvas.drawBitmap(image, left, top, paint)
    }
}
