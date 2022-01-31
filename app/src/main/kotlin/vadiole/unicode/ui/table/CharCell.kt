package vadiole.unicode.ui.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.dp
import vadiole.unicode.utils.ktx.onClick
import vadiole.unicode.utils.ktx.onLongClick

class CharCell(
    context: Context,
    appTheme: AppTheme,
    private val delegate: Delegate
) : View(context), ThemeDelegate {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 17f.dp(context)
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private var charY: Float = 0f
    private var charX: Float = 0f
    private var position: Int = -1
    private var char: String? = null

    init {
        appTheme.observe(this)
        isClickable = true
        isFocusable = true
        onClick = {
            delegate.onClick(position)
        }
        onLongClick = {
            delegate.onLongClick(position)
        }
    }

    fun bind(p: Int, c: String) {
        position = p
        char = c
        invalidate()
    }

    override fun onMeasure(width: Int, height: Int) = super.onMeasure(width, width)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        charX = w / 2f
        charY = 0.5f * h - 0.5f * (textPaint.descent() + textPaint.ascent())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = char
        if (c != null) {
            canvas.drawText(c, charX, charY, textPaint)
        }
    }

    override fun hasOverlappingRendering() = false

    override fun applyTheme(theme: Theme) {
        textPaint.color = theme.getColor(key_windowTextPrimary)
        background = theme.getRippleCircle(key_windowRipple)
        invalidate()
    }

    interface Delegate {
        fun onClick(position: Int)
        fun onLongClick(position: Int)
    }
}