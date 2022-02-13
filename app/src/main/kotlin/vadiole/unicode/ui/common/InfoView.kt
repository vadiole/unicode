package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.dp

class InfoView(context: Context, theme: AppTheme) : View(context), ThemeDelegate {
    private val backgroundDrawable = SquircleDrawable(12.dp(context))
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f.dp(context)
        typeface = roboto_regular
        style = Paint.Style.FILL
        isSubpixelText = true
        letterSpacing = 0.02f
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f.dp(context)
        typeface = roboto_regular
        style = Paint.Style.FILL
        isSubpixelText = true
        letterSpacing = 0.02f
    }
    private var viewCenterX: Float = 0f
    private val valueCoordinateY = 25f.dp(context)
    private val nameCoordinateY = 44f.dp(context)
    private var name: String = "Code"
    private var value: String = "U+0041"

    init {
        theme.observe(this)
        isClickable = true
        isFocusable = true
        background = backgroundDrawable
    }

    fun bind(infoName: String, infoValue: String) {
        name = infoName
        value = infoValue
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(56.dp(context), MeasureSpec.EXACTLY))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewCenterX = 0.5f * w
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(value, viewCenterX, valueCoordinateY, valuePaint)
        canvas.drawText(name, viewCenterX, nameCoordinateY, namePaint)
    }

    override fun applyTheme(theme: Theme) {
        backgroundDrawable.colors = theme.getColors(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed)),
            arrayOf(key_dialogSurfacePressed, key_dialogSurface)
        )
        valuePaint.color = theme.getColor(key_windowTextPrimary)
        namePaint.color = theme.getColor(key_windowTextSecondary)
    }
}