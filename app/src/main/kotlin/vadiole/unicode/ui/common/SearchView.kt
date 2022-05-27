package vadiole.unicode.ui.common

import android.content.Context
import android.widget.EditText
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_searchFieldBackground
import vadiole.unicode.utils.extension.dp

class SearchView(context: Context) : EditText(context), ThemeDelegate {
    private val backgroundDrawable = SquircleDrawable(10.dp(context))

    init {
        themeManager.observe(this)
        background = backgroundDrawable
    }

    override fun applyTheme() {
        backgroundDrawable.colors = themeManager.getColors(key_searchFieldBackground)
    }
}