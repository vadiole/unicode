package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import vadiole.unicode.R

class EdgeVeil(context: Context) {
    private class Segment(val mask: Shader, val paint: Paint) {
        val bounds = RectF()
    }

    private val density = context.resources.displayMetrics.density
    private val matrix = Matrix()
    private val colors = IntArray(17) { index ->
        val t = index / 16f
        Color.argb((t * t * (3f - 2f * t) * 255).roundToInt(), 255, 255, 255)
    }
    private val noiseShader = BitmapShader(createNoise(context.getColor(R.color.layer0)), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val segments = Array(8) { index ->
        val mask = if (index < 4) {
            LinearGradient(0f, 0f, 1f, 0f, colors, null, Shader.TileMode.CLAMP)
        } else {
            RadialGradient(0f, 0f, 1f, colors, null, Shader.TileMode.CLAMP)
        }
        Segment(mask, Paint(Paint.DITHER_FLAG).apply {
            shader = ComposeShader(noiseShader, mask, PorterDuff.Mode.DST_IN)
        })
    }
    private var width = 0
    private var height = 0

    var strength = 0.8f
    var sizeDp = 96f
        set(value) {
            if (field == value) return
            field = value
            updateGeometry()
        }

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        updateGeometry()
    }

    private fun updateGeometry() {
        val edge = (sizeDp * density).roundToInt().coerceIn(0, min(width, height) / 2).toFloat()
        if (edge == 0f) {
            segments.forEach { it.bounds.setEmpty() }
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        segments[0].bounds.set(edge, 0f, w - edge, edge)
        segments[1].bounds.set(edge, h - edge, w - edge, h)
        segments[2].bounds.set(0f, edge, edge, h - edge)
        segments[3].bounds.set(w - edge, edge, w, h - edge)
        setLinear(0, 0f, edge, 0f, 0f)
        setLinear(1, 0f, h - edge, 0f, h)
        setLinear(2, edge, 0f, 0f, 0f)
        setLinear(3, w - edge, 0f, w, 0f)
        segments[4].bounds.set(0f, 0f, edge, edge)
        segments[5].bounds.set(w - edge, 0f, w, edge)
        segments[6].bounds.set(0f, h - edge, edge, h)
        segments[7].bounds.set(w - edge, h - edge, w, h)
        setCorner(4, edge, edge, edge)
        setCorner(5, w - edge, edge, edge)
        setCorner(6, edge, h - edge, edge)
        setCorner(7, w - edge, h - edge, edge)
    }

    private fun setLinear(index: Int, startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy)
        matrix.setSinCos(dy / length, dx / length)
        matrix.preScale(length, 1f)
        matrix.postTranslate(startX, startY)
        segments[index].mask.setLocalMatrix(matrix)
    }

    private fun setCorner(index: Int, centerX: Float, centerY: Float, radius: Float) {
        matrix.setScale(radius, radius)
        matrix.postTranslate(centerX, centerY)
        segments[index].mask.setLocalMatrix(matrix)
    }

    private fun createNoise(color: Int): Bitmap {
        var noise = 1
        val pixels = IntArray(64 * 64) {
            noise = noise * 1664525 + 1013904223
            val grain = ((noise ushr 8) / 16777216f * 5f).toInt() - 2
            Color.rgb(
                (Color.red(color) + grain).coerceIn(0, 255),
                (Color.green(color) + grain).coerceIn(0, 255),
                (Color.blue(color) + grain).coerceIn(0, 255),
            )
        }
        return Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
        }
    }

    fun draw(canvas: Canvas, opacity: Float) {
        val alpha = (opacity * strength * 255).roundToInt()
        for (segment in segments) {
            segment.paint.alpha = alpha
            canvas.drawRect(segment.bounds, segment.paint)
        }
    }
}
