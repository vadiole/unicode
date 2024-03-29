package vadiole.unicode

import android.annotation.SuppressLint
import android.app.Application
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig

@SuppressLint("StaticFieldLeak")
class UnicodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        unicodeStorageInternal = UnicodeStorage(this)
        userConfigInternal = UserConfig(this)
        if (userConfig.crashReportDisabled) {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        }
    }

    companion object {
        private var unicodeStorageInternal: UnicodeStorage? = null
        val unicodeStorage: UnicodeStorage
            get() = requireNotNull(unicodeStorageInternal)

        private var userConfigInternal: UserConfig? = null
        val userConfig: UserConfig
            get() = requireNotNull(userConfigInternal)
    }
}