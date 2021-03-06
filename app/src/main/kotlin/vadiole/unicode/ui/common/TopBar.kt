package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.*

class TopBar(
    context: Context,
    appTheme: AppTheme,
    title: String,
    onTitleClick: TextView.() -> Unit = {}
) : FrameLayout(context), ThemeDelegate {
    private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
        setLineHeightX(22.dp(context))
        typeface = roboto_semibold
        includeFontPadding = false
        gravity = Gravity.CENTER
        onClick = onTitleClick
        letterSpacing = 0.03f
        text = title
    }

    init {
        appTheme.observe(this)
        addView(titleView, frameParams(matchParent, 50.dp(context), gravity = Gravity.BOTTOM))
    }

    override fun onDraw(canvas: Canvas) {
        val dividerY = height - 1f
        canvas.drawLine(0f, dividerY, width.toFloat(), dividerY, sharedDividerPaint)
    }

    override fun applyTheme(theme: Theme) {
        setBackgroundColor(theme.getColor(key_topBarBackground))
        titleView.setTextColor(theme.getColor(key_windowTextPrimary))
    }

    fun setTitle(text: String) {
        titleView.text = text
    }
}