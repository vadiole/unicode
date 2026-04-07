package vadiole.unicode

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.RecentRepository
import vadiole.unicode.data.config.UserConfig

@SuppressLint("StaticFieldLeak")
class UnicodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        unicodeStorageInternal = UnicodeStorage(this)
        userConfigInternal = UserConfig(this)
        recentRepositoryInternal = RecentRepository(this)
        Handler(Looper.getMainLooper()).postDelayed({ recentRepository.load() }, 1000)
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

        private var recentRepositoryInternal: RecentRepository? = null
        val recentRepository: RecentRepository
            get() = requireNotNull(recentRepositoryInternal)
    }
}