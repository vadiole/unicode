package vadiole.unicode

import kotlinx.coroutines.Dispatchers
import vadiole.unicode.data.CharStorage
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.isDarkMode

class AppComponent(app: UnicodeApp) {
    val charsStorage = CharStorage(app, Dispatchers.IO)
    val theme = AppTheme(
        if (app.applicationContext.resources.configuration.isDarkMode) {
            AppTheme.Scheme.BLUE_DARK
        } else {
            AppTheme.Scheme.BLUE_LIGHT
        }
    )
}