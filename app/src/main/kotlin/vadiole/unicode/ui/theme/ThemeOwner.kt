package vadiole.unicode.ui.theme

import vadiole.unicode.UnicodeApp
import vadiole.unicode.ui.common.ContextOwner

interface ThemeOwner : ContextOwner, OnAttachCallback {
    val theme: AppTheme get() = ((getContext().applicationContext) as UnicodeApp).appComponent.theme
    fun invalidateTheme()
}