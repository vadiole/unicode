package vadiole.unicode.ui.table

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.SparseBooleanArray
import kotlin.math.ceil
import vadiole.unicode.BuildConfig

class EmojiPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val canvas = Canvas()
    private val bounds = Rect()

    fun begin(strip: Bitmap, textSize: Float) {
        strip.eraseColor(Color.TRANSPARENT)
        canvas.setBitmap(strip)
        paint.textSize = textSize
    }

    fun draw(strip: Bitmap, char: String, index: Int, ink: IntArray): Boolean {
        val cell = strip.height
        val offset = index * 4
        paint.getTextBounds(char, 0, char.length, bounds)
        if (bounds.isEmpty || bounds.width() > cell || bounds.height() > cell) {
            ink.fill(0, offset, offset + 4)
            return false
        }
        val x = cell * index + (cell - bounds.width()) / 2 - bounds.left
        val y = (cell - bounds.height()) / 2 - bounds.top
        canvas.drawText(char, x.toFloat(), y.toFloat(), paint)
        ink[offset] = x + bounds.left
        ink[offset + 1] = y + bounds.top
        ink[offset + 2] = x + bounds.right
        ink[offset + 3] = y + bounds.bottom
        return true
    }

    fun end() = canvas.setBitmap(null)
}

object EmojiGlyphs {
    private const val CELL_RATIO = 1.4f
    private const val MAX_FREE = 8
    val painter = EmojiPainter()
    private val colored = SparseBooleanArray()
    private var pixels = IntArray(0)
    private val free = arrayOfNulls<Bitmap>(MAX_FREE)
    private var freeCount = 0
    private var freeHeight = 0
    private var debugLayout = false
    val showIndicators: Boolean
        get() = BuildConfig.DEBUG && debugLayout

    fun updateIndicators(): Boolean {
        val enabled = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "debug.layout", false) as Boolean
        }.getOrDefault(false)
        if (enabled == debugLayout) return false
        debugLayout = enabled
        return true
    }

    fun obtainStrip(width: Int, height: Int): Bitmap {
        if (height != freeHeight) clearFree(height)
        while (freeCount > 0) {
            val strip = free[--freeCount]!!
            free[freeCount] = null
            if (strip.width == width) return strip
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun releaseStrip(strip: Bitmap) {
        if (strip.height == freeHeight && freeCount < MAX_FREE) free[freeCount++] = strip
    }

    fun clearFree(height: Int = 0) {
        free.fill(null)
        freeCount = 0
        freeHeight = height
    }

    fun isCandidate(codePoint: Int) = codePoint in 0x1F000..0x1FAFF || codePoint in 0x2190..0x2BFF

    fun mayBeColored(codePoint: Int): Boolean {
        if (!isCandidate(codePoint)) return false
        val known = colored.indexOfKey(codePoint)
        return known < 0 || colored.valueAt(known)
    }

    fun cellSize(textSize: Float) = ceil(textSize * CELL_RATIO).toInt() + 2

    fun draw(strip: Bitmap, codePoint: Int, char: String, index: Int, ink: IntArray): Boolean {
        if (!painter.draw(strip, char, index, ink)) return false
        val known = colored.indexOfKey(codePoint)
        if (known >= 0) return true
        val cell = strip.height
        val isColored = hasColor(strip, cell * index, cell)
        colored.put(codePoint, isColored)
        return isColored
    }

    private fun hasColor(strip: Bitmap, left: Int, cell: Int): Boolean {
        if (pixels.size < cell * cell) pixels = IntArray(cell * cell)
        strip.getPixels(pixels, 0, cell, left, 0, cell, cell)
        for (index in 0 until cell * cell) {
            val pixel = pixels[index]
            val green = pixel shr 8 and 0xFF
            if ((pixel shr 16 and 0xFF) != green || (pixel and 0xFF) != green) return true
        }
        return false
    }
}

class EmojiSharpener {
    private val thread = HandlerThread("emoji", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val painter = EmojiPainter()
    private var released = false

    fun render(chars: Array<String?>, textSize: Float, onReady: (Bitmap, IntArray) -> Unit) {
        handler.post {
            val cell = EmojiGlyphs.cellSize(textSize)
            val strip = Bitmap.createBitmap(cell * chars.size, cell, Bitmap.Config.ARGB_8888)
            val ink = IntArray(chars.size * 4)
            painter.begin(strip, textSize)
            chars.forEachIndexed { index, char -> if (char != null) painter.draw(strip, char, index, ink) }
            painter.end()
            mainHandler.post { if (!released) onReady(strip, ink) }
        }
    }

    fun release() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }
}
