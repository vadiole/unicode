package vadiole.unicode.data.config

import android.content.Context

class UserConfig(context: Context) {
    companion object {
        private const val key_showUnsupportedChars = "showUnsupportedChars"
        private const val key_firstCrashReport = "firstCrashReport"
        private const val key_crashReportDisabled = "crashReportDisabled"
        private const val key_usedPinchToZoom = "usedPinchToZoom"
        private const val key_usedFastScroll = "usedFastScroll"
        private const val key_collected = "collected"
        private const val key_usedCollect = "usedCollect"
        private const val key_usedCopy = "usedCopy"
        private const val key_recentsEnabled = "recentsEnabled"
        private const val key_dockEnabled = "dockEnabled"
        private const val key_centerTitle = "centerTitle"
        private const val key_mergedDetailsActions = "mergedDetailsActions"
        private const val key_topSearchEligible = "topSearchEligible"
        private const val key_searchAtTop = "searchAtTop"
        private const val key_topSearchEverEnabled = "topSearchEverEnabled"
        private const val key_tableScrollCodePoint = "tableScrollCodePoint"
        private const val key_tableScrollOffset = "tableScrollOffset"
        private const val key_dockScroll = "dockScroll"
        private const val key_tableCharacterScale = "tableCharacterScale"
    }

    private val sharedPreferences = context.getSharedPreferences("unicode", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()

    init {
        if (!sharedPreferences.contains(key_dockEnabled) || !sharedPreferences.contains(key_centerTitle) ||
            !sharedPreferences.contains(key_mergedDetailsActions) || !sharedPreferences.contains(key_topSearchEligible)
        ) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val isUpdate = info.firstInstallTime != info.lastUpdateTime
            if (!sharedPreferences.contains(key_dockEnabled)) editor.putBoolean(key_dockEnabled, !isUpdate)
            if (!sharedPreferences.contains(key_centerTitle)) editor.putBoolean(key_centerTitle, isUpdate)
            if (!sharedPreferences.contains(key_mergedDetailsActions)) editor.putBoolean(key_mergedDetailsActions, !isUpdate)
            if (!sharedPreferences.contains(key_topSearchEligible)) {
                editor.putBoolean(key_topSearchEligible, sharedPreferences.getBoolean(key_centerTitle, isUpdate))
            }
            editor.apply()
        }
    }

    val topSearchEligible: Boolean
        get() = sharedPreferences.getBoolean(key_topSearchEligible, false)

    // Retained to retire top search later only for users who never enabled it.
    val topSearchEverEnabled: Boolean
        get() = sharedPreferences.getBoolean(key_topSearchEverEnabled, false)

    var searchAtTop: Boolean
        get() = topSearchEligible && sharedPreferences.getBoolean(key_searchAtTop, false)
        set(value) {
            if (!topSearchEligible) return
            editor.putBoolean(key_searchAtTop, value)
            if (value) editor.putBoolean(key_topSearchEverEnabled, true)
            editor.apply()
        }

    val mergedDetailsActions: Boolean
        get() = sharedPreferences.getBoolean(key_mergedDetailsActions, false)

    val centerTitle: Boolean
        get() = sharedPreferences.getBoolean(key_centerTitle, false)

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

    var collected: String
        get() = sharedPreferences.getString(key_collected, "") ?: ""
        set(value) {
            editor.putString(key_collected, value).apply()
        }

    var usedCollect: Boolean
        get() = sharedPreferences.getBoolean(key_usedCollect, false)
        set(value) {
            editor.putBoolean(key_usedCollect, value).apply()
        }

    var usedCopy: Boolean
        get() = sharedPreferences.getBoolean(key_usedCopy, false)
        set(value) {
            editor.putBoolean(key_usedCopy, value).apply()
        }

    var recentsEnabled: Boolean
        get() = sharedPreferences.getBoolean(key_recentsEnabled, true)
        set(value) {
            editor.putBoolean(key_recentsEnabled, value).apply()
        }

    var tableCharacterScale: Float
        get() = sharedPreferences.getFloat(key_tableCharacterScale, 1f)
        set(value) {
            editor.putFloat(key_tableCharacterScale, value).apply()
        }

    var dockEnabled: Boolean
        get() = sharedPreferences.getBoolean(key_dockEnabled, true)
        set(value) {
            editor.putBoolean(key_dockEnabled, value).apply()
        }

    var tableScrollCodePoint: Int
        get() = sharedPreferences.getInt(key_tableScrollCodePoint, -1)
        set(value) {
            editor.putInt(key_tableScrollCodePoint, value).apply()
        }

    var tableScrollOffset: Int
        get() = sharedPreferences.getInt(key_tableScrollOffset, 0)
        set(value) {
            editor.putInt(key_tableScrollOffset, value).apply()
        }

    var dockScroll: Int
        get() = sharedPreferences.getInt(key_dockScroll, -1)
        set(value) {
            editor.putInt(key_dockScroll, value).apply()
        }
}
