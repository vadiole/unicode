package vadiole.unicode

import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.theme.Theme

interface AppScope : AppContextOwner {
    val appComponent: AppComponent
        get() = (getApplicationContext() as UnicodeApp).appComponent
    val theme: Theme
        get() = (getApplicationContext() as UnicodeApp).appComponent.theme

    val unicodeStorage: UnicodeStorage
        get() = (getApplicationContext() as UnicodeApp).appComponent.charsStorage

    val userConfig: UserConfig
        get() = (getApplicationContext() as UnicodeApp).appComponent.userConfig
}