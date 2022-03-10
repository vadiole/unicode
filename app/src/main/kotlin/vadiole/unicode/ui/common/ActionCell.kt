package vadiole.unicode.ui.common

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.DrawableRes
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.setPaddingHorizontal

class ActionCell(
    context: Context,
    theme: AppTheme,
    name: String,
    private val topItem: Boolean = false,
    private val bottomItem: Boolean = false,
) : TextView(context), ThemeDelegate {
    private val backgroundDrawable = SquircleDrawable(13.dp(context)).apply {
        when {
            topItem -> {
                skipBottomRight = true
                skipBottomLeft = true
            }
            bottomItem -> {
                skipTopLeft = true
                skipTopRight = true
            }
        }
    }

    init {
        theme.observe(this)
        background = backgroundDrawable
        text = name
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        minHeight = 48.dp(context)
        includeFontPadding = false
        typeface = roboto_regular
        letterSpacing = 0.03f
        textSize = 17f
        isClickable = true
        isFocusable = true
        setPaddingHorizontal(16.dp(context))
    }

    fun setIcon(@DrawableRes iconId: Int) {
        setCompoundDrawablesWithIntrinsicBounds(0, 0, iconId, 0)
    }

    override fun applyTheme(theme: Theme) {
        backgroundDrawable.colors = theme.getColors(
            arrayOf(intArrayOf(-android.R.attr.state_pressed), intArrayOf(android.R.attr.state_pressed)),
            arrayOf(key_dialogSurface, key_dialogSurfacePressed)
        )
        compoundDrawableTintList = theme.getColors(key_windowTextPrimary)
        setTextColor(theme.getColor(key_windowTextPrimary))
    }
}