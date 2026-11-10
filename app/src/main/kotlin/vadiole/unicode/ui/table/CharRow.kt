package vadiole.unicode.ui.table

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.floor
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
    val charSize: Float
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
            updateInkTransforms()
            invalidate()
        }
    var enlargeSmallChars = false
        set(value) {
            if (field == value) return
            field = value
            if (value) loadInk()
            updateInkTransforms()
            invalidate()
        }
    private val inkBoxes = FloatArray(count * 4)
    private val inkScales = FloatArray(count) { 1f }
    private val inkPivotsX = FloatArray(count)
    private val inkPivotsY = FloatArray(count)
    private val charRipples: BooleanArray = BooleanArray(count)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spriteSource = Rect()
    private val spriteTarget = RectF()
    private val isSprite = BooleanArray(count)
    private val spriteInk = IntArray(count * 4)
    private var hasEmojiCandidates = false
    private var spriteGeneration = 0
    private var sprites: Bitmap? = null
    var spriteTextSize = 0f
        private set
    var isSpritePreview = false
        private set
    private val previewTintPaint = Paint().apply { color = PREVIEW_TINT }
    private val stretchedTintPaint = Paint().apply { color = STRETCHED_TINT }
    val hasEmojiSprites: Boolean
        get() = spriteTextSize > 0f
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
        if (enlargeSmallChars) loadInk()
        updateInkTransforms()
        hasEmojiCandidates = false
        for (value in codePoints.storage) {
            if (EmojiGlyphs.isCandidate(value)) {
                hasEmojiCandidates = true
                break
            }
        }
        if (hasEmojiCandidates) loadSprites(charSize * PREVIEW_SCALE) else releaseSprites()
        removeCallbacks(clearHighlightRunnable)
        clearHighlight()
        charRipples.fill(false)
        clearRipple()
        invalidate()
    }

    fun sharpenEmoji(textSize: Float, sharpener: EmojiSharpener, onDone: Runnable) {
        val generation = spriteGeneration
        val chars = arrayOfNulls<String>(count)
        val storage = codePoints.storage
        for (index in storage.indices) {
            if (isSprite[index]) chars[index] = CodePoint(storage[index]).char
        }
        sharpener.render(chars, textSize) { strip, ink ->
            if (generation == spriteGeneration) {
                sprites?.let(EmojiGlyphs::releaseStrip)
                sprites = strip
                ink.copyInto(spriteInk)
                spriteTextSize = textSize
                isSpritePreview = false
                invalidate()
            }
            onDone.run()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restoreSprites()
    }

    fun releaseSprites() {
        spriteGeneration++
        spriteTextSize = 0f
        sprites?.let(EmojiGlyphs::releaseStrip)
        sprites = null
    }

    fun restoreSprites() {
        if (hasEmojiCandidates && spriteTextSize == 0f) {
            loadSprites(charSize * PREVIEW_SCALE)
            invalidate()
        }
    }

    private fun loadSprites(textSize: Float) {
        spriteGeneration++
        spriteTextSize = 0f
        var strip: Bitmap? = null
        val storage = codePoints.storage
        for (index in storage.indices) {
            isSprite[index] = false
            val codePoint = CodePoint(storage[index])
            if (!EmojiGlyphs.mayBeColored(codePoint.value) || abbreviations.containsKey(codePoint)) continue
            val target = strip ?: obtainSprites(EmojiGlyphs.cellSize(textSize)).also {
                EmojiGlyphs.painter.begin(it, textSize)
                strip = it
            }
            if (EmojiGlyphs.draw(target, codePoint.value, codePoint.char, index, spriteInk)) {
                GlyphInk.load(codePoint, inkBoxes, index * 4)
                isSprite[index] = true
                spriteTextSize = textSize
                isSpritePreview = true
            }
        }
        if (strip != null) EmojiGlyphs.painter.end()
        if (spriteTextSize == 0f) releaseSprites()
    }

    private fun obtainSprites(cell: Int): Bitmap {
        val current = sprites
        if (current != null && current.height == cell) return current
        current?.let(EmojiGlyphs::releaseStrip)
        return EmojiGlyphs.obtainStrip(cell * count, cell).also { sprites = it }
    }

    private fun drawSprite(canvas: Canvas, index: Int, bitmap: Bitmap): Boolean {
        val offset = index * 4
        val centerX = charCoordsX[index]
        val size = charSize
        if (spriteInk[offset + 2] <= spriteInk[offset] || spriteInk[offset + 3] <= spriteInk[offset + 1]) return false
        spriteSource.set(spriteInk[offset], spriteInk[offset + 1], spriteInk[offset + 2], spriteInk[offset + 3])
        spriteTarget.set(
            centerX + inkBoxes[offset] * size,
            charCoordY + inkBoxes[offset + 1] * size,
            centerX + inkBoxes[offset + 2] * size,
            charCoordY + inkBoxes[offset + 3] * size,
        )
        canvas.drawBitmap(bitmap, spriteSource, spriteTarget, spritePaint)
        if (EmojiGlyphs.showIndicators) {
            when {
                isSpritePreview -> canvas.drawRect(spriteTarget, previewTintPaint)
                spriteTextSize != size -> canvas.drawRect(spriteTarget, stretchedTintPaint)
            }
        }
        return true
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
                        val scale = inkScales[index]
                        val centerX = inkPivotsX[index] + (charCoordsX[index] - inkPivotsX[index]) * scale
                        val centerY = inkPivotsY[index] + (charPivotY - inkPivotsY[index]) * scale
                        delegate.onClick(codePoint, this, centerX, centerY, charSize * scale)
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
        releaseSprites()
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
        updateInkTransforms()
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
            val scale = inkScales[index]
            val bitmap = sprites
            if (spriteTextSize > 0f && isSprite[index] && bitmap != null && drawSprite(canvas, index, bitmap)) return@forEachIndexed
            if (scale > 1f) {
                val save = canvas.save()
                canvas.scale(scale, scale, inkPivotsX[index], inkPivotsY[index])
                drawChar(canvas, codePoint, charCoordX, charCoordY)
                canvas.restoreToCount(save)
            } else {
                drawChar(canvas, codePoint, charCoordX, charCoordY)
            }
        }
    }

    private fun loadInk() {
        codePoints.forEachIndexed { index, codePoint ->
            if (abbreviations.containsKey(codePoint)) {
                inkBoxes.fill(0f, index * 4, index * 4 + 4)
            } else {
                GlyphInk.load(codePoint, inkBoxes, index * 4)
            }
        }
    }

    private fun updateInkTransforms() {
        inkScales.fill(1f)
        if (!enlargeSmallChars || width == 0) return
        val cellWidth = width.toFloat() / count
        val halfCell = cellWidth / 2f
        for (index in 0 until codePoints.size) {
            val offset = index * 4
            val inkWidth = inkBoxes[offset + 2] - inkBoxes[offset]
            val inkHeight = inkBoxes[offset + 3] - inkBoxes[offset + 1]
            val extent = maxOf(inkWidth, inkHeight)
            if (extent <= 0f || extent >= SMALL_EXTENT) continue
            val scale = minOf(MAX_BOOST, SMALL_EXTENT / extent, cellWidth / (inkWidth * charSize), height / (inkHeight * charSize))
            if (scale <= 1f) continue
            val centerX = charCoordsX[index]
            inkScales[index] = scale
            inkPivotsX[index] = inkPivot(
                centerX + inkBoxes[offset] * charSize,
                centerX + inkBoxes[offset + 2] * charSize,
                centerX - halfCell,
                centerX + halfCell,
            )
            inkPivotsY[index] = inkPivot(
                charCoordY + inkBoxes[offset + 1] * charSize,
                charCoordY + inkBoxes[offset + 3] * charSize,
                0f,
                height.toFloat(),
            )
        }
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
        const val SMALL_EXTENT = 0.6f
        const val MAX_BOOST = 2f
        const val PREVIEW_SCALE = 0.5f
        const val PREVIEW_TINT = 0x66FF3B30
        const val STRETCHED_TINT = 0x66007AFF
    }
}