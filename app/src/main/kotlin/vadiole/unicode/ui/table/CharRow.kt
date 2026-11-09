package vadiole.unicode.ui.table

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.floor
import kotlin.math.sqrt
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.rippleDrawable

class CharRow(
    context: Context,
    private val count: Int,
    private val delegate: Delegate,
) : View(context) {
    private var codePoints: CodePointArray = CodePointArray(0)
    private var abbreviations: Map<CodePoint, String> = emptyMap()
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val abbreviationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val longClickRunnable = object : Runnable {
        override fun run() {
            val index = actionDownIndex
            if (index >= 0) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val codePoint = codePoints[index]
                delegate.onLongClick(codePoint)
                releaseClick(index)
            }
        }
    }
    private val clearHighlightRunnable = object : Runnable {
        override fun run() = clearHighlight()
    }
    private val longClickDuration: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baseCharSize = 144f.dp(context) / count
    private val charSize: Float
        get() = baseCharSize * characterScale
    private val abbreviationSize: Float
        get() = charSize * 0.5f
    var characterScale = 1f
        set(value) {
            if (field == value) return
            field = value
            charPaint.textSize = charSize
            abbreviationPaint.textSize = abbreviationSize
            updateBaselines(height)
            invalidate()
        }
    var enlargeSmallChars = false
        set(value) {
            if (field == value) return
            field = value
            if (value) measureInk()
            invalidate()
        }
    private val measurePaint = Paint(charPaint).apply { textAlign = Paint.Align.LEFT }
    private val inkBounds = Rect()
    private val inkBoxes = FloatArray(count * 4)
    private val boosts = FloatArray(count) { 1f }
    private val charRipples: BooleanArray = BooleanArray(count)
    private val charCoordsX: FloatArray = FloatArray(count)
    private var charCoordY: Float = 0f
    private var abbreviationCoordY: Float = 0f
    private var charPivotY: Float = 0f
    private val maxRippleRadius = 200f.dp(context) / count
    private val rippleInset = 1.5f.dp(context)
    private var rippleRadius: Float = maxRippleRadius
    private var actionDownIndex = -1
    private var highlightIndex = -1
    private val ripple = context.rippleDrawable(mask = ShapeDrawable(OvalShape())).apply {
        callback = this@CharRow
    }

    init {
        charPaint.color = context.getColor(R.color.windowTextPrimary)
        abbreviationPaint.color = context.getColor(R.color.characterAbbreviation)
        ripplePaint.color = context.getColor(R.color.ripple)
        charPaint.textSize = charSize
        abbreviationPaint.textSize = abbreviationSize
        isClickable = true
        isFocusable = true
    }

    fun bind(codePoints: CodePointArray, abbreviations: Map<CodePoint, String>) {
        this.codePoints = codePoints
        this.abbreviations = abbreviations
        if (enlargeSmallChars) measureInk()
        removeCallbacks(clearHighlightRunnable)
        clearHighlight()
        charRipples.fill(false)
        clearRipple()
        invalidate()
    }

    fun highlightChar(index: Int) {
        clearHighlight()
        charRipples[index] = true
        highlightIndex = index
        handler.postDelayed(clearHighlightRunnable, 2000)
    }

    private fun clearHighlight() {
        if (highlightIndex != -1) {
            charRipples[highlightIndex] = false
            invalidate()
            highlightIndex = -1
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = floor(event.x / width * count).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (index !in codePoints.indices) return false
                startRipple(index, event.x, event.y)
                postDelayed(longClickRunnable, longClickDuration)
            }

            MotionEvent.ACTION_UP -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex == index) {
                        val codePoint = codePoints[index]
                        delegate.onClick(codePoint, this, charCoordsX[index], charPivotY, charSize * inkScale(index))
                    }
                    releaseClick(actionDownIndex)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (actionDownIndex >= 0) {
                    releaseClick(actionDownIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex != index) {
                        releaseClick(actionDownIndex)
                        return false
                    }
                    ripple.setHotspot(event.x, event.y)
                }
            }
        }
        return true
    }

    private fun releaseClick(index: Int) {
        if (actionDownIndex != index) return
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        ripple.state = EMPTY_STATE
    }

    private fun startRipple(index: Int, x: Float, y: Float) {
        clearRipple()
        actionDownIndex = index
        val centerX = charCoordsX[index]
        ripple.setBounds(
            (centerX - rippleRadius).toInt(),
            (charPivotY - rippleRadius).toInt(),
            (centerX + rippleRadius).toInt(),
            (charPivotY + rippleRadius).toInt(),
        )
        ripple.setHotspot(x, y)
        ripple.state = PRESSED_STATE
    }

    private fun clearRipple() {
        ripple.state = EMPTY_STATE
        ripple.jumpToCurrentState()
        actionDownIndex = -1
        removeCallbacks(longClickRunnable)
        invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who === ripple || super.verifyDrawable(who)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        ripple.jumpToCurrentState()
    }

    override fun onDetachedFromWindow() {
        clearRipple()
        removeCallbacks(clearHighlightRunnable)
        clearHighlight()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val singleCharWidth = w / count
        val centerOffsetX = singleCharWidth / 2f
        repeat(count) { index ->
            charCoordsX[index] = singleCharWidth * index + centerOffsetX
        }
        updateBaselines(h)
        charPivotY = h / 2f
        rippleRadius = minOf(maxRippleRadius, minOf(singleCharWidth, h) / 2f - rippleInset)
    }

    private fun updateBaselines(height: Int) {
        charCoordY = (height - charPaint.descent() - charPaint.ascent()) / 2
        abbreviationCoordY = (height - abbreviationPaint.descent() - abbreviationPaint.ascent()) / 2
    }

    override fun onMeasure(width: Int, height: Int) {
        val widthSize = MeasureSpec.getSize(width)
        val heightSpec = MeasureSpec.makeMeasureSpec(widthSize / count, MeasureSpec.EXACTLY)
        super.onMeasure(width, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        ripple.draw(canvas)
        codePoints.forEachIndexed { index, codePoint ->
            val charCoordX = charCoordsX[index]
            val charPivotX = charCoordsX[index]
            if (charRipples[index]) {
                canvas.drawCircle(charPivotX, charPivotY, rippleRadius, ripplePaint)
            }
            val scale = inkScale(index)
            if (scale > 1f) {
                val offset = index * 4
                val left = charCoordX + inkBoxes[offset] * charSize
                val top = charCoordY + inkBoxes[offset + 1] * charSize
                val right = charCoordX + inkBoxes[offset + 2] * charSize
                val bottom = charCoordY + inkBoxes[offset + 3] * charSize
                val halfCell = width / count / 2f
                val save = canvas.save()
                canvas.scale(
                    scale,
                    scale,
                    inkPivot(left, right, charCoordX - halfCell, charCoordX + halfCell),
                    inkPivot(top, bottom, 0f, height.toFloat()),
                )
                drawChar(canvas, codePoint, charCoordX, charCoordY)
                canvas.restoreToCount(save)
            } else {
                drawChar(canvas, codePoint, charCoordX, charCoordY)
            }
        }
    }

    private fun measureInk() {
        measurePaint.textSize = charPaint.textSize
        val size = measurePaint.textSize
        codePoints.forEachIndexed { index, codePoint ->
            boosts[index] = 1f
            if (abbreviations.containsKey(codePoint)) return@forEachIndexed
            val char = codePoint.char
            measurePaint.getTextBounds(char, 0, char.length, inkBounds)
            if (inkBounds.isEmpty) return@forEachIndexed
            val originX = measurePaint.measureText(char) / 2f
            val offset = index * 4
            inkBoxes[offset] = (inkBounds.left - originX) / size
            inkBoxes[offset + 1] = inkBounds.top / size
            inkBoxes[offset + 2] = (inkBounds.right - originX) / size
            inkBoxes[offset + 3] = inkBounds.bottom / size
            val extent = maxOf(inkBounds.width(), inkBounds.height()) / size
            if (extent < SMALL_EXTENT) boosts[index] = minOf(MAX_BOOST, sqrt(SMALL_EXTENT / extent))
        }
    }

    private fun inkScale(index: Int): Float {
        if (!enlargeSmallChars || index >= codePoints.size) return 1f
        val boost = boosts[index]
        if (boost <= 1f) return 1f
        val offset = index * 4
        val inkWidth = (inkBoxes[offset + 2] - inkBoxes[offset]) * charSize
        val inkHeight = (inkBoxes[offset + 3] - inkBoxes[offset + 1]) * charSize
        return maxOf(1f, minOf(boost, width.toFloat() / count / inkWidth, height / inkHeight))
    }

    private fun inkPivot(start: Float, end: Float, areaStart: Float, areaEnd: Float): Float {
        val before = maxOf(0f, start - areaStart)
        val after = maxOf(0f, areaEnd - end)
        val space = before + after
        return if (space > 0f) start + (end - start) * before / space else (start + end) / 2f
    }

    private fun drawChar(canvas: Canvas, codePoint: CodePoint, charCoordX: Float, charCoordY: Float) {
        val abbreviation = abbreviations[codePoint]
        if (abbreviation != null) {
            canvas.drawText(abbreviation, charCoordX, abbreviationCoordY, abbreviationPaint)
        } else {
            canvas.drawText(codePoint.char, charCoordX, charCoordY, charPaint)
        }
    }

    override fun hasOverlappingRendering() = false

    interface Delegate {
        fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float)
        fun onLongClick(codePoint: CodePoint)
    }

    private companion object {
        val PRESSED_STATE = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
        val EMPTY_STATE = intArrayOf()
        const val SMALL_EXTENT = 0.45f
        const val MAX_BOOST = 1.6f
    }
}