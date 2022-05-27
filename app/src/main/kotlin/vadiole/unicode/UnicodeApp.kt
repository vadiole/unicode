package vadiole.unicode

import android.annotation.SuppressLint
import android.app.Application
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.theme.ThemeManager
import vadiole.unicode.ui.theme.blue_dark
import vadiole.unicode.ui.theme.blue_light
import vadiole.unicode.utils.extension.isDarkMode

@SuppressLint("StaticFieldLeak")
class UnicodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        _unicodeStorage = UnicodeStorage(this)
        _userConfig = UserConfig(this)
        _themeManager = ThemeManager(if (resources.configuration.isDarkMode) blue_dark else blue_light)
    }

    companion object {
        private var _unicodeStorage: UnicodeStorage? = null
        val unicodeStorage: UnicodeStorage
            get() = requireNotNull(_unicodeStorage)

        private var _userConfig: UserConfig? = null
        val userConfig: UserConfig
            get() = requireNotNull(_userConfig)
        private var _themeManager: ThemeManager? = null
        val themeManager: ThemeManager
            get() = requireNotNull(_themeManager)
    }
}