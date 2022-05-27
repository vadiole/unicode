package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_topBarBackground
import vadiole.unicode.ui.theme.key_windowTextPrimary
import vadiole.unicode.ui.theme.roboto_semibold
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.onClick
import vadiole.unicode.utils.extension.setLineHeightX

class TopBar(
    context: Context,
    title: String,
    onTitleClick: TextView.() -> Unit = {},
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
        themeManager.observe(this)
        addView(titleView, frameParams(matchParent, 50.dp(context), gravity = Gravity.BOTTOM))
    }

    override fun onDraw(canvas: Canvas) {
        val dividerY = height - 1f
        canvas.drawLine(0f, dividerY, width.toFloat(), dividerY, themeManager.dividerPaint)
    }

    override fun applyTheme() {
        setBackgroundColor(themeManager.getColor(key_topBarBackground))
        titleView.setTextColor(themeManager.getColor(key_windowTextPrimary))
    }

    fun setTitle(text: String) {
        titleView.text = text
    }
}