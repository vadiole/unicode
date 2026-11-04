package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.text.TextPaint
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class EmojiFireworksView(context: Context, private val emojis: List<String>) : View(context) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()
    private val glyphSize = 30f.dp(context)
    private val startX = FloatArray(COUNT)
    private val vx = FloatArray(COUNT)
    private val vy = FloatArray(COUNT)
    private val scale = FloatArray(COUNT)
    private val startRotation = FloatArray(COUNT)
    private val spin = FloatArray(COUNT)
    private val delay = LongArray(COUNT)
    private val bitmapIndex = IntArray(COUNT)
    @Volatile private var bitmaps: Array<Bitmap>? = null
    private var startedAt = 0L
    private var launchY = 0f
    private var gravity = 0f
    private var finishedAt = 0L
    private var isRunning = false

    var topInset = 0

    val isReady: Boolean
        get() = !isRunning && SystemClock.uptimeMillis() >= finishedAt + COOLDOWN

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        Thread { bitmaps = renderBitmaps() }.start()
    }

    private fun renderBitmaps(): Array<Bitmap> {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = glyphSize
            textAlign = Paint.Align.CENTER
        }
        val side = (glyphSize * 1.3f).toInt()
        val baseline = side / 2f - (paint.ascent() + paint.descent()) / 2f
        return emojis.filter(paint::hasGlyph).map { emoji ->
            Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawText(emoji, side / 2f, baseline, paint)
                prepareToDraw()
            }
        }.toTypedArray()
    }

    fun start() {
        val bitmaps = bitmaps
        if (!isReady || width == 0 || bitmaps.isNullOrEmpty()) return
        val reach = width / REFERENCE_WIDTH
        launchY = height * (1f - LAUNCH_HEIGHT)
        gravity = GRAVITY * reach
        for (i in 0 until COUNT) {
            val depth = Random.nextFloat()
            val fromLeft = Random.nextBoolean()
            val angle = Math.toRadians(MIN_ANGLE + Random.nextDouble() * (MAX_ANGLE - MIN_ANGLE))
            val speed = (MIN_SPEED + Random.nextFloat() * (MAX_SPEED - MIN_SPEED)) * (0.9f + depth * 0.2f) * reach
            startX[i] = if (fromLeft) 0f else width.toFloat()
            vx[i] = cos(angle).toFloat() * speed * if (fromLeft) 1f else -1f
            vy[i] = sin(angle).toFloat() * speed
            scale[i] = 0.55f + depth * 0.35f
            startRotation[i] = Random.nextFloat() * 80f - 40f
            spin[i] = (40f + Random.nextFloat() * 60f) * if (Random.nextBoolean()) 1f else -1f
            delay[i] = (Random.nextFloat() * SHOOTING_DURATION).toLong()
            bitmapIndex[i] = Random.nextInt(bitmaps.size)
        }
        startedAt = SystemClock.uptimeMillis()
        isRunning = true
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val bitmaps = bitmaps
        if (!isRunning || bitmaps == null) return
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val terminal = gravity / DRAG
        var alive = false
        val clip = canvas.save()
        canvas.clipRect(0, topInset, width, height)
        for (i in 0 until COUNT) {
            val t = (elapsed - delay[i]) / 1000f
            if (t < 0f) {
                alive = true
                continue
            }
            val bitmap = bitmaps[bitmapIndex[i]]
            val slowdown = (1f - exp(-DRAG * t)) / DRAG
            val px = startX[i] + vx[i] * slowdown
            val py = launchY - (vy[i] + terminal) * slowdown + terminal * t
            if (py - bitmap.width * scale[i] > height) continue
            alive = true
            drawMatrix.setTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            drawMatrix.postScale(scale[i], scale[i])
            drawMatrix.postRotate(startRotation[i] + spin[i] * t)
            drawMatrix.postTranslate(px, py)
            canvas.drawBitmap(bitmap, drawMatrix, bitmapPaint)
        }
        canvas.restoreToCount(clip)
        if (alive) {
            postInvalidateOnAnimation()
        } else {
            isRunning = false
            finishedAt = SystemClock.uptimeMillis()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
    }

    private companion object {
        const val COUNT = 72
        const val COOLDOWN = 1000L
        const val SHOOTING_DURATION = 1400f
        const val LAUNCH_HEIGHT = 0.6f
        const val MIN_ANGLE = 55.0
        const val MAX_ANGLE = 72.0
        const val MIN_SPEED = 650f
        const val MAX_SPEED = 1200f
        const val GRAVITY = 1600f
        const val DRAG = 1.8f
        const val REFERENCE_WIDTH = 411f
    }
}
