package vadiole.unicode

import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.theme.Theme
import vadiole.unicode.util.extension.isDarkMode

class AppComponent(app: UnicodeApp) {
    val charsStorage = UnicodeStorage(app)
    val userConfig = UserConfig(app)
    val theme = Theme(
        if (app.applicationContext.resources.configuration.isDarkMode) {
            Theme.blueDark
        } else {
            Theme.blueLight
        }
    )
}