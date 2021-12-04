package vadiole.unicode.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat.setLineHeight
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.dp
import vadiole.unicode.utils.fill
import vadiole.unicode.utils.frame
import vadiole.unicode.utils.onClick

class Toolbar(
    context: Context,
    appTheme: AppTheme,
    title: String,
    onTitleClick: TextView.() -> Unit = {}
) : FrameLayout(context), ThemeDelegate {

    private val dividerPaint = Paint().apply {
        strokeWidth = 1f
    }

    private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
        typeface = roboto_semibold
        gravity = Gravity.CENTER
        setLineHeight(this, 22.dp(context))
        text = title
        onClick = onTitleClick
    }

    init {
        appTheme.observe(this)
        addView(titleView, frame(fill, 50.dp(context), gravity = Gravity.BOTTOM))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height - 1f
        canvas.drawLine(0f, y, width.toFloat(), y, dividerPaint)
    }

    override fun applyTheme(theme: Theme) {
        setBackgroundColor(theme.getColor(key_toolBarBackground))
        titleView.setTextColor(theme.getColor(key_windowTextPrimary))
        dividerPaint.color = theme.getColor(key_windowDivider)
        invalidate()
    }
}