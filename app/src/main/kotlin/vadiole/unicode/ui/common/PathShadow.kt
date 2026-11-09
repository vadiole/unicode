package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.ceil

class PathShadow(context: Context) {
    private val ambientBlur = 36f.dp(context)
    private val ambientOffset = 10f.dp(context)
    private val contactBlur = 5f.dp(context)
    private val contactOffset = 2f.dp(context)
    private val padding = ceil(ambientBlur * 1.75f + ambientOffset)
    private val shape = Path()
    private val local = Path()
    private val difference = Path()
    private val bounds = RectF()
    private val destination = RectF()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private var bitmap: Bitmap? = null

    fun update(path: Path) {
        if (path.isEmpty) {
            shape.reset()
            bitmap = null
            return
        }
        path.computeBounds(bounds, true)
        local.set(path)
        local.offset(-bounds.left, -bounds.top)
        difference.set(shape)
        if (bitmap == null ||!difference.op(local, Path.Op.XOR) || !difference.isEmpty) {
            shape.set(local)
            bitmap = render(bounds.width(), bounds.height())
        }
        val image = bitmap ?: return
        destination.set(0f, 0f, image.width / SCALE, image.height / SCALE)
        destination.offset(bounds.left - padding, bounds.top - padding)
    }

    private fun render(width: Float, height: Float): Bitmap {
        val image = Bitmap.createBitmap(
            ceil((width + padding * 2) * SCALE).toInt(),
            ceil((height + padding * 2) * SCALE).toInt(),
            Bitmap.Config.ARGB_8888,
        ).apply { density = Bitmap.DENSITY_NONE }
        matrix.setTranslate(padding, padding)
        matrix.postScale(SCALE, SCALE)
        local.transform(matrix)
        val canvas = Canvas(image)
        shadowPaint.setShadowLayer(ambientBlur * SCALE, 0f, ambientOffset * SCALE, Color.argb(17, 0, 0, 0))
        canvas.drawPath(local, shadowPaint)
        shadowPaint.setShadowLayer(contactBlur * SCALE, 0f, contactOffset * SCALE, Color.argb(20, 0, 0, 0))
        canvas.drawPath(local, shadowPaint)
        return image
    }

    fun draw(canvas: Canvas, opacity: Float) {
        val image = bitmap ?: return
        paint.alpha = (opacity * 255).toInt()
        canvas.drawBitmap(image, null, destination, paint)
    }

    private companion object {
        const val SCALE = 0.5f
    }
}
