package vadiole.unicode.data.config

import android.content.Context

class UserConfig(context: Context) {
    companion object {
        private const val key_showUnsupportedChars = "showUnsupportedChars"
        private const val key_firstCrashReport = "firstCrashReport"
        private const val key_crashReportDisabled = "crashReportDisabled"
        private const val key_usedPinchToZoom = "usedPinchToZoom"
        private const val key_usedFastScroll = "usedFastScroll"
        private const val key_searchResultGrid = "searchResultGrid"
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

    var usedPinchToZoom: Boolean
        get() = sharedPreferences.getBoolean(key_usedPinchToZoom, false)
        set(value) {
            editor.putBoolean(key_usedPinchToZoom, value).apply()
        }

    var usedFastScroll: Boolean
        get() = sharedPreferences.getBoolean(key_usedFastScroll, false)
        set(value) {
            editor.putBoolean(key_usedFastScroll, value).apply()
        }

    var searchResultGrid: Boolean
        get() = sharedPreferences.getBoolean(key_searchResultGrid, false)
        set(value) {
            editor.putBoolean(key_searchResultGrid, value).apply()
        }
}