package vadiole.unicode.data.config

import android.content.Context

class UserConfig(context: Context) {
    companion object {
        private const val key_showUnsupportedChars = "showUnsupportedChars"
        private const val key_firstCrashReport = "firstCrashReport"
        private const val key_crashReportDisabled = "crashReportDisabled"
    }

    private val sharedPreferences = context.getSharedPreferences("unicode", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()

    var showUnsupportedChars: Boolean
        get() = sharedPreferences.getBoolean(key_showUnsupportedChars, false)
        set(value) {
            editor.putBoolean(key_showUnsupportedChars, value).apply()
        }

    var firstCrashReport: Boolean
        get() = sharedPreferences.getBoolean(key_firstCrashReport, true)
        set(value) {
            editor.putBoolean(key_firstCrashReport, value).commit()
        }

    var crashReportDisabled: Boolean
        get() = sharedPreferences.getBoolean(key_crashReportDisabled, false)
        set(value) {
            editor.putBoolean(key_crashReportDisabled, value).commit()
        }
}