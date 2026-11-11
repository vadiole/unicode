package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import vadiole.unicode.R

class DotsLoader(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.windowTextSecondary) }
    private val radius = 3f.dp(context)
    private val step = 14f.dp(context)
    private var startTime = 0L

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (radius * 2 * MAX_SCALE).toInt() + 1
        setMeasuredDimension((step * (COUNT - 1)).toInt() + size, size)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startTime = SystemClock.uptimeMillis()
    }

    override fun onDraw(canvas: Canvas) {
        val animate = animatorsEnabled
        val wave = if (animate) (SystemClock.uptimeMillis() - startTime) % PERIOD * COUNT / PERIOD.toFloat() else -COUNT.toFloat()
        val left = (width - step * (COUNT - 1)) / 2f
        val cy = height / 2f
        for (i in 0 until COUNT) {
            val distance = abs(wave - i - 0.5f).let { minOf(it, COUNT - it) }
            val intensity = (1f - distance).coerceAtLeast(0f)
            paint.alpha = (255 * (MIN_ALPHA + (1f - MIN_ALPHA) * intensity)).toInt()
            canvas.drawCircle(left + step * i, cy, radius * (1f + (MAX_SCALE - 1f) * intensity), paint)
        }
        if (animate && isShown) postInvalidateOnAnimation()
    }

    private companion object {
        const val COUNT = 5
        const val PERIOD = 500L
        const val MIN_ALPHA = 0.3f
        const val MAX_SCALE = 1.3f
    }
}
