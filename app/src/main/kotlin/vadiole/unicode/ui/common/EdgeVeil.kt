package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import vadiole.unicode.R

class EdgeVeil(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val target = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        color = context.getColor(R.color.layer0)
    }
    private var bitmap: Bitmap? = null

    fun setSize(width: Int, height: Int) {
        target.set(0f, 0f, width.toFloat(), height.toFloat())
        if (width <= 0 || height <= 0) {
            bitmap = null
            return
        }
        val maskWidth = ceil(width / density).toInt()
        val maskHeight = ceil(height / density).toInt()
        val image = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ALPHA_8)
        val pixels = ByteArray(image.rowBytes * maskHeight)
        val halfWidth = maskWidth / 2f
        val halfHeight = maskHeight / 2f
        val falloff = minOf(32f, halfWidth, halfHeight)
        val radius = falloff
        var noise = 1
        for (y in 0 until maskHeight) {
            val qy = kotlin.math.abs(y + 0.5f - halfHeight) - (halfHeight - radius)
            for (x in 0 until maskWidth) {
                if (x >= falloff && x < maskWidth - falloff && y >= falloff && y < maskHeight - falloff) continue
                val qx = kotlin.math.abs(x + 0.5f - halfWidth) - (halfWidth - radius)
                val dx = max(qx, 0f)
                val dy = max(qy, 0f)
                val distance = radius - sqrt(dx * dx + dy * dy) - min(max(qx, qy), 0f)
                val t = (distance / falloff).coerceIn(0f, 1f)
                val alpha = (1f - t * t * (3f - 2f * t)) * 0.25f * 255f
                noise = noise * 1664525 + 1013904223
                val threshold = (noise ushr 8) / 16777216f
                pixels[y * image.rowBytes + x] = (alpha + threshold).toInt().coerceIn(0, 255).toByte()
            }
        }
        image.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        bitmap = image
    }

    fun draw(canvas: Canvas, opacity: Float) {
        val image = bitmap ?: return
        paint.alpha = (opacity * 255).toInt()
        canvas.drawBitmap(image, null, target, paint)
    }
}
