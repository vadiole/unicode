package vadiole.unicode.ui.common

import vadiole.unicode.UnicodeApp
import vadiole.unicode.ui.theme.AppTheme

interface ThemeOwner : ContextOwner, OnAttachCallback {
    val theme: AppTheme get() = ((getContext().applicationContext) as UnicodeApp).appComponent.theme
    fun invalidateTheme()
}