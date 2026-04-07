package vadiole.unicode.data.config

import android.content.Context
import android.content.SharedPreferences
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray

class RecentRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "recents"
        private const val KEY_RECENTS = "recents"
        private const val MAX_RECENTS = 200
    }

    private var sharedPreferences: SharedPreferences? = null
    private var loaded = false
    private val recents: MutableList<Int> = mutableListOf()

    fun load() {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences = prefs
        val raw = prefs.getString(KEY_RECENTS, null)
        if (!raw.isNullOrEmpty()) {
            try {
                raw.split(",").mapTo(recents) { it.toInt() }
            } catch (_: NumberFormatException) {
                // corrupted data, start fresh
            }
        }
    }

    fun record(codePoint: CodePoint) {
        if (!loaded) load()
        recents.remove(codePoint.value)
        recents.add(0, codePoint.value)
        if (recents.size > MAX_RECENTS) {
            recents.removeAt(recents.size - 1)
        }
        saveToDisk()
    }

    fun getRecents(): CodePointArray {
        if (!loaded) load()
        return CodePointArray(recents.size) { index -> CodePoint(recents[index]) }
    }

    fun count(): Int = recents.size

    fun clear() {
        recents.clear()
        saveToDisk()
    }

    private fun saveToDisk() {
        val raw = recents.joinToString(",")
        sharedPreferences?.edit()?.putString(KEY_RECENTS, raw)?.apply()
    }
}
