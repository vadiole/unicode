package vadiole.unicode

import vadiole.unicode.data.CharStorage
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.extension.isDarkMode

class AppComponent(app: UnicodeApp) {
    val charsStorage = CharStorage(app)
    val theme = AppTheme(
        if (app.applicationContext.resources.configuration.isDarkMode) {
            AppTheme.Scheme.BLUE_DARK
        } else {
            AppTheme.Scheme.BLUE_LIGHT
        }
    )
}