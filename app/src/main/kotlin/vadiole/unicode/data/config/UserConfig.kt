package vadiole.unicode.data.config

import android.content.Context

class UserConfig(context: Context) {
    companion object {
        private const val key_showUnsupportedChars = "showUnsupportedChars"
    }

    private val sharedPreferences = context.getSharedPreferences("unicode", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()

    var showUnsupportedChars: Boolean
        get() = sharedPreferences.getBoolean(key_showUnsupportedChars, false)
        set(value) {
            editor.putBoolean(key_showUnsupportedChars, value).apply()
        }

}