package vadiole.unicode

import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.extension.isDarkMode

class AppComponent(app: UnicodeApp) {
    val charsStorage = UnicodeStorage(app)
    val userConfig = UserConfig(app)
    val theme = AppTheme(
        if (app.applicationContext.resources.configuration.isDarkMode) {
            AppTheme.Scheme.BLUE_DARK
        } else {
            AppTheme.Scheme.BLUE_LIGHT
        }
    // minor changes 2
    )
}
