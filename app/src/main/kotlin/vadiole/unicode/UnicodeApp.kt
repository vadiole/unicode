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
        unicodeStorageInternal = UnicodeStorage(this)
        userConfigInternal = UserConfig(this)
        themeManagerInternal = ThemeManager(if (resources.configuration.isDarkMode) blue_dark else blue_light)
    }

    companion object {
        private var unicodeStorageInternal: UnicodeStorage? = null
        val unicodeStorage: UnicodeStorage
            get() = requireNotNull(unicodeStorageInternal)

        private var userConfigInternal: UserConfig? = null
        val userConfig: UserConfig
            get() = requireNotNull(userConfigInternal)
        private var themeManagerInternal: ThemeManager? = null
        val themeManager: ThemeManager
            get() = requireNotNull(themeManagerInternal)
    }
}
