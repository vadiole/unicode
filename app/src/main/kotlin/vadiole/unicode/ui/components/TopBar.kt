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
import vadiole.unicode.utils.frameParams
import vadiole.unicode.utils.ktx.onClick
import vadiole.unicode.utils.matchParent

class TopBar(
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
        setLineHeight(this, 22.dp(context))
        typeface = roboto_semibold
        gravity = Gravity.CENTER
        onClick = onTitleClick
        text = title
    }

    init {
        appTheme.observe(this)
        addView(titleView, frameParams(matchParent, 50.dp(context), gravity = Gravity.BOTTOM))
    }

    override fun onDraw(canvas: Canvas) {
        val dividerY = height - 1f
        canvas.drawLine(0f, dividerY, width.toFloat(), dividerY, dividerPaint)
    }

    override fun applyTheme(theme: Theme) {
        setBackgroundColor(theme.getColor(key_topBarBackground))
        titleView.setTextColor(theme.getColor(key_windowTextPrimary))
        dividerPaint.color = theme.getColor(key_windowDivider)
    }
}