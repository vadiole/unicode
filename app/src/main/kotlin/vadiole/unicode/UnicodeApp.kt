package vadiole.unicode

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.RecentRepository
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.table.TableController

@SuppressLint("StaticFieldLeak")
class UnicodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler(this))
        userConfigInternal = UserConfig(this)
        if (!processName().endsWith(":sendcrash")) {
            unicodeStorageInternal = UnicodeStorage(this).also { it.search.prepare() }
            TableController.preload(unicodeStorage, userConfig.showUnsupportedChars)
            recentRepositoryInternal = RecentRepository(this, userConfig)
            Handler(Looper.getMainLooper()).postDelayed({ recentRepository.load() }, 1000)
        }
        if (userConfig.crashReportDisabled) {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        }
    }

    private fun processName(): String = if (Build.VERSION.SDK_INT >= 28) {
        getProcessName()
    } else {
        File("/proc/self/cmdline").readText().substringBefore('\u0000')
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