package vadiole.unicode.ui.table.search

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.ui.common.SimpleTextView
import vadiole.unicode.ui.common.StateColorDrawable
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_windowTextPrimary
import vadiole.unicode.ui.theme.keysWindowPressable
import vadiole.unicode.ui.theme.roboto_regular
import vadiole.unicode.ui.theme.statesPressable
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.onClick

class SearchResultCell(context: Context, delegate: Delegate) : FrameLayout(context), ThemeDelegate {

    interface Delegate {
        fun onClick(codePoint: CodePoint)
    }

    private val backgroundDrawable = StateColorDrawable()
    private var codePoint: CodePoint? = null
    val charView = SimpleTextView(context).apply {
        textSize = 24f.dp(context)
    }
    val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        typeface = (roboto_regular)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(0, 0, 16.dp(context), 0)
    }

    init {
        themeManager.observe(this)
        layoutParams = RecyclerView.LayoutParams(matchParent, 48.dp(context))
        addView(name, frameParams(matchParent, 48.dp(context), marginLeft = 64.dp(context)))
        addView(charView, frameParams(64.dp(context), 48.dp(context), gravity = Gravity.LEFT))
        background = backgroundDrawable
        isClickable = true
        isFocusable = true
        clipChildren = false
        onClick = {
            val codePoint = codePoint
            if (codePoint != null) {
                delegate.onClick(codePoint)
            }
        }
    }

    fun bind(data: SearchResult) {
        name.text = data.name
        charView.text = data.codePoint.char
        codePoint = data.codePoint
    }

    override fun applyTheme() {
        charView.textColor = themeManager.getColor(key_windowTextPrimary)
        name.setTextColor(themeManager.getColor(key_windowTextPrimary))
        backgroundDrawable.colors = themeManager.getColors(
            statesPressable,
            keysWindowPressable
        )
    }
}