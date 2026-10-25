package vadiole.unicode.ui.table.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import vadiole.unicode.R
import vadiole.unicode.ui.common.dp
import kotlin.math.hypot

class FlyOverlay(context: Context, private val delegate: Delegate) : View(context) {
    interface Delegate {
        fun getSlotCenter(slotLeft: Float, slotRight: Float, out: FloatArray)
        fun onLanded(end: Int)
    }

    private val launch = 96f.dp(context)
    private val referenceDistance = 400f.dp(context)
    private val dockGlyphSize = DockView.glyphSize(context)
    private val charColor = context.getColor(R.color.windowTextPrimary)
    private val abbreviationColor = ColorUtils.setAlphaComponent(charColor, 128)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val active = ArrayList<Flyer>()
    private val pool = ArrayList<Flyer>()
    private val target = FloatArray(2)
    private val tmpLocation = IntArray(2)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun launch(
        label: String,
        isAbbreviation: Boolean,
        source: View,
        centerX: Float,
        centerY: Float,
        glyphSize: Float,
        slotLeft: Float,
        slotRight: Float,
        end: Int,
    ) {
        getLocationInWindow(tmpLocation)
        val originX = tmpLocation[0]
        val originY = tmpLocation[1]
        source.getLocationInWindow(tmpLocation)
        val flyer = if (pool.isEmpty()) Flyer() else pool.removeAt(pool.lastIndex)
        flyer.label = label
        flyer.isAbbreviation = isAbbreviation
        flyer.startX = tmpLocation[0] - originX + centerX * source.scaleX
        flyer.startY = tmpLocation[1] - originY + centerY * source.scaleY
        flyer.textSize = if (isAbbreviation) glyphSize * 0.5f else glyphSize
        flyer.endScale = dockGlyphSize / glyphSize
        flyer.slotLeft = slotLeft
        flyer.slotRight = slotRight
        flyer.end = end
        delegate.getSlotCenter(slotLeft, slotRight, target)
        val x0 = flyer.startX
        val x2 = target[0] - originX
        val away = if (x2 < x0) -launch else launch
        flyer.away = if (isInside(x0, x0 + away, x2, flyer.textSize / 2f)) away else -away
        val distance = hypot(x2 - x0, target[1] - originY - flyer.startY)
        flyer.start(stiffness = (BASE_STIFFNESS * referenceDistance / distance.coerceAtLeast(1f)).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS))
        active.add(flyer)
        invalidate()
    }

    fun cancelAll() {
        for (i in active.indices.reversed()) release(active[i])
        invalidate()
    }

    private fun release(flyer: Flyer) {
        flyer.stop()
        active.remove(flyer)
        pool.add(flyer)
    }

    override fun onDraw(canvas: Canvas) {
        if (active.isEmpty()) return
        getLocationInWindow(tmpLocation)
        for (i in active.indices) {
            val flyer = active[i]
            delegate.getSlotCenter(flyer.slotLeft, flyer.slotRight, target)
            val p = flyer.progress.value
            val x0 = flyer.startX
            val y0 = flyer.startY
            val x2 = target[0] - tmpLocation[0]
            val y2 = target[1] - tmpLocation[1]
            val x1 = x0 + flyer.away
            val y1 = y0 - launch
            val q = 1f - p
            val x = q * q * x0 + 2f * q * p * x1 + p * p * x2
            val y = q * q * y0 + 2f * q * p * y1 + p * p * y2
            val scale = 1f + (flyer.endScale - 1f) * p.coerceIn(0f, 1f)
            paint.textSize = flyer.textSize
            paint.color = if (flyer.isAbbreviation) abbreviationColor else charColor
            val baseline = -(paint.ascent() + paint.descent()) / 2f
            val count = canvas.save()
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            canvas.drawText(flyer.label, 0f, baseline, paint)
            canvas.restoreToCount(count)
        }
    }

    private fun isInside(x0: Float, x1: Float, x2: Float, halfSize: Float): Boolean {
        val denominator = x0 - 2f * x1 + x2
        val t = if (denominator == 0f) 0f else ((x0 - x1) / denominator).coerceIn(0f, 1f)
        val q = 1f - t
        val extreme = q * q * x0 + 2f * q * t * x1 + t * t * x2
        return extreme - halfSize >= 0f && extreme + halfSize <= width
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAll()
    }

    private inner class Flyer {
        var label = ""
        var isAbbreviation = false
        var startX = 0f
        var startY = 0f
        var textSize = 0f
        var endScale = 1f
        var slotLeft = 0f
        var slotRight = 0f
        var end = 0
        var away = 0f
        val progress = FloatValueHolder()
        private val progressSpring = SpringAnimation(progress).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.85f
            }
            setMinimumVisibleChange(0.001f)
            addUpdateListener { _, value, _ ->
                if (value >= LANDING) land() else invalidate()
            }
        }
        fun start(stiffness: Float) {
            progress.value = 0f
            progressSpring.spring.stiffness = stiffness
            progressSpring.setStartValue(0f)
            progressSpring.start()
        }

        fun stop() {
            progressSpring.cancel()
        }

        private fun land() {
            val end = end
            release(this)
            invalidate()
            delegate.onLanded(end)
        }
    }

    private companion object {
        const val BASE_STIFFNESS = 140f
        const val MIN_STIFFNESS = 70f
        const val MAX_STIFFNESS = 350f
        const val LANDING = 0.98f
    }
}
