package vadiole.unicode.ui.table

import android.content.Context
import vadiole.unicode.ui.common.SimpleTextView
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.onClick
import vadiole.unicode.utils.extension.onLongClick

class CharCell(
    context: Context,
    appTheme: AppTheme,
    private val delegate: Delegate
) : SimpleTextView(context), ThemeDelegate {
    private var codePoint: Int = -1

    init {
        appTheme.observe(this)
        textSize = 17f.dp(context)
        isClickable = true
        isFocusable = true
        onClick = {
            delegate.onClick(codePoint)
        }
        onLongClick = {
            delegate.onLongClick(codePoint)
        }
    }

    fun bind(codePoint: Int, char: String) {
        this.codePoint = codePoint
        text = char
    }

    override fun onMeasure(width: Int, height: Int) = super.onMeasure(width, width)

    override fun applyTheme(theme: Theme) {
        textColor = theme.getColor(key_windowTextPrimary)
        background = theme.getRippleCircle(key_windowRipple)
        invalidate()
    }

    interface Delegate {
        fun onClick(codePoint: Int)
        fun onLongClick(codePoint: Int)
    }
}