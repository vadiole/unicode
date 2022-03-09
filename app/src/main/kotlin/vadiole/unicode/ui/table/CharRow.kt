package vadiole.unicode.ui.table

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.dp
import kotlin.math.floor

class CharRow(
    context: Context,
    appTheme: AppTheme,
    private val count: Int,
    private val delegate: Delegate
) : View(context), ThemeDelegate {
    private var codePoints: Array<CodePoint> = emptyArray()
    private val charPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val charSize = 144f.dp(context) / count
    private val charRipples: BooleanArray = BooleanArray(count)
    private val charCoordsX: FloatArray = FloatArray(count)
    private var charCoordY: Float = 0f
    private var charPivotY: Float = 0f
    private var rippleRadius: Float = 200f.dp(context) / count
    private var actionDownIndex = -1

    init {
        appTheme.observe(this)
        charPaint.textSize = charSize
        isClickable = true
        isFocusable = true

    }

    fun bind(codePoints: Array<CodePoint>) {
        this.codePoints = codePoints
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = floor(event.x / width * count).toInt()
        Log.d("TOUCH", "onTouchEvent: ${event.actionMasked}")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                actionDownIndex = index
                charRipples[index] = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (actionDownIndex >= 0) {
                    if (actionDownIndex == index) {
                        val codePoint = codePoints[index]
                        delegate.onClick(codePoint)
                    }
                    charRipples[actionDownIndex] = false
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (actionDownIndex >= 0) {
                    charRipples[actionDownIndex] = false
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (actionDownIndex >= 0 && actionDownIndex != index) {
                    charRipples[actionDownIndex] = false
                    actionDownIndex = -1
                    invalidate()
                }
            }
        }
        return true
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val singleCharWidth = w / count
        val centerOffsetX = singleCharWidth / 2f
        repeat(count) { index ->
            charCoordsX[index] = singleCharWidth * index + centerOffsetX
        }
        charCoordY = (h - charPaint.descent() - charPaint.ascent()) / 2
        charPivotY = h / 2f
    }

    override fun onMeasure(width: Int, height: Int) {
        val widthSize = MeasureSpec.getSize(width)
        val heightSpec = MeasureSpec.makeMeasureSpec(widthSize / count, MeasureSpec.EXACTLY)
        super.onMeasure(width, heightSpec)
    }

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas) {
        codePoints.forEachIndexed { index, codePoint ->
            val text = codePoint.char
            val charCoordX = charCoordsX[index]
            val charPivotX = charCoordsX[index]
            if (charRipples[index]) {
                canvas.drawCircle(charPivotX, charPivotY, rippleRadius, ripplePaint)
            }
            canvas.drawText(text, charCoordX, charCoordY, charPaint)
        }
    }

    override fun hasOverlappingRendering() = false

    override fun applyTheme(theme: Theme) {
        charPaint.color = theme.getColor(key_windowTextPrimary)
        ripplePaint.color = theme.getColor(key_windowRipple)
        invalidate()
    }

    interface Delegate {
        fun onClick(codePoint: CodePoint)
        fun onLongClick(codePoint: CodePoint)
    }
}