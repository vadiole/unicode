package vadiole.unicode.ui.common

import vadiole.unicode.BuildConfig

object DebugLayout {
    private var enabled = false
    val isShown: Boolean
        get() = BuildConfig.DEBUG && enabled

    fun refresh(): Boolean {
        if (!BuildConfig.DEBUG) return false
        enabled = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "debug.layout", false) as Boolean
        }.getOrDefault(false)
        return enabled
    }
}
