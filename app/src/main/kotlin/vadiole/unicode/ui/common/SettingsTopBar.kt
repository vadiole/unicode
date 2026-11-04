package vadiole.unicode.ui.common

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.setAccessibilityHeading
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updatePadding
import vadiole.unicode.R
import vadiole.unicode.ui.extension.onClick

class SettingsTopBar(context: Context, title: String, onBack: () -> Unit) : FrameLayout(context) {
    private val barHeight = 48.dp(context)
    private val borderPaint = android.graphics.Paint().apply {
        color = context.getColor(R.color.floatingBorder)
        strokeWidth = borderWidth(context)
    }

    init {
        setBackgroundColor(context.getColor(R.color.layer1))
        setWillNotDraw(false)
        addView(IconButton(context, context.getDrawable(R.drawable.ic_chevron_left)!!, floating = false).apply {
            setPadding(4.dp(context), 0, 4.dp(context), 0)
            contentDescription = context.getString(R.string.settings_back)
            onClick = { onBack() }
        }, frameParams(56.dp(context), barHeight, Gravity.LEFT or Gravity.BOTTOM))
        addView(TextView(context).apply {
            textSize = TEXT_TITLE
            typeface = font_semibold
            includeFontPadding = false
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            setTextColor(context.getColor(R.color.windowTextPrimary))
            text = title
            setAccessibilityHeading(this, true)
        }, frameParams(wrapContent, barHeight, Gravity.LEFT or Gravity.BOTTOM, marginLeft = 56.dp(context)))
        setOnApplyWindowInsetsListener(this) { _, insets ->
            updatePadding(top = insets.statusBars.top)
            insets
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(barHeight + paddingTop, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val y = height - borderPaint.strokeWidth / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, borderPaint)
    }
}
