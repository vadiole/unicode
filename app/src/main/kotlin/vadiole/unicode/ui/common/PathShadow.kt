package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class PathShadow(
    context: Context,
    private val spec: Spec = card(context),
    private val onReady: () -> Unit = {},
) {
    class Layer(val blur: Float, val dy: Float, val alpha: Float, val dx: Float = 0f, val spread: Float = 0f)

    class Spec(val layers: List<Layer>, val color: Int = Color.BLACK, val darkThemeAlpha: Float = 2f)

    private val alphaScale = if (context.resources.configuration.isDarkMode) spec.darkThemeAlpha else 1f
    private val padding = ceil(spec.layers.maxOf { it.blur * 1.5f + maxOf(abs(it.dx), abs(it.dy)) + it.spread })
    private val shape = Path()
    private val local = Path()
    private val difference = Path()
    private val bounds = RectF()
    private val paint = Paint().apply { color = spec.color }
    private val handler = Handler(Looper.getMainLooper())
    private var bitmap: Bitmap? = null
    private var baking = false
    private var generation = 0
    private var left = 0f
    private var top = 0f

    fun update(path: Path) {
        if (path.isEmpty) {
            shape.reset()
            generation++
            bitmap = null
            baking = false
            return
        }
        path.computeBounds(bounds, true)
        left = (bounds.left - padding).roundToInt().toFloat()
        top = (bounds.top - padding).roundToInt().toFloat()
        local.set(path)
        local.offset(-bounds.left, -bounds.top)
        difference.set(shape)
        if ((bitmap != null || baking) && difference.op(local, Path.Op.XOR) && difference.isEmpty) return
        shape.set(local)
        bitmap = null
        baking = true
        val id = ++generation
        val snapshot = Path(local)
        val width = ceil(bounds.width() + padding * 2).toInt()
        val height = ceil(bounds.height() + padding * 2).toInt()
        executor.execute {
            val image = bake(snapshot, width, height)
            handler.post {
                if (id != generation) return@post
                bitmap = image
                baking = false
                onReady()
            }
        }
    }

    fun draw(canvas: Canvas, opacity: Float) {
        val image = bitmap ?: return
        paint.alpha = (Color.alpha(spec.color) * opacity).roundToInt()
        canvas.drawBitmap(image, left, top, paint)
    }

    private fun bake(path: Path, width: Int, height: Int): Bitmap {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
            density = Bitmap.DENSITY_NONE
        }
        val canvas = Canvas(image)
        val rowBytes = image.rowBytes
        val size = rowBytes * height
        val layers = Array(spec.layers.size) { ByteArray(size) }
        val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
        spec.layers.forEachIndexed { index, layer ->
            image.eraseColor(Color.TRANSPARENT)
            val sigma = layer.blur / 2f
            layerPaint.maskFilter = if (sigma > 0.5f) {
                BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            } else {
                null
            }
            layerPaint.style = if (layer.spread > 0f) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            layerPaint.strokeWidth = layer.spread * 2f
            canvas.save()
            canvas.translate(padding + layer.dx, padding + layer.dy)
            canvas.drawPath(path, layerPaint)
            canvas.restore()
            image.copyPixelsToBuffer(ByteBuffer.wrap(layers[index]))
        }
        val alphas = FloatArray(layers.size) { (spec.layers[it].alpha * alphaScale).coerceAtMost(1f) / 255f }
        val output = ByteArray(size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * rowBytes + x
                var clear = 1f
                for (layer in layers.indices) {
                    val value = layers[layer][index].toInt() and 0xFF
                    if (value != 0) clear *= 1f - value * alphas[layer]
                }
                if (clear >= 1f) continue
                output[index] = ((1f - clear) * 255f + noise(x, y)).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        image.copyPixelsFromBuffer(ByteBuffer.wrap(output))
        return image
    }

    private fun noise(x: Int, y: Int): Float {
        var hash = x * 374761393 + y * 668265263
        hash = (hash xor (hash ushr 13)) * 1274126177
        hash = hash xor (hash ushr 16)
        return ((hash and 0xFFFF) + (hash ushr 16)) / 65535f - 1f
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()

        fun card(context: Context) = Spec(
            listOf(
                Layer(blur = 42f.dp(context), dy = 10f.dp(context), alpha = 0.067f),
                Layer(blur = 6f.dp(context), dy = 2f.dp(context), alpha = 0.078f),
            ),
        )
    }
}
