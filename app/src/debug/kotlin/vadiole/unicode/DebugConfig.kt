package vadiole.unicode

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.lang.reflect.Modifier
import vadiole.unicode.data.config.UserConfig

class DebugConfig : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (savedInstanceState == null) activity.intent.extras?.let { apply(activity, it) }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == "help") { "Unknown method $method, expected help" }
        return Bundle().apply { putString("help", help(requireNotNull(context))) }
    }

    @SuppressLint("ApplySharedPref")
    private fun apply(context: Context, extras: Bundle) {
        val keys = extras.keySet().filter { it.startsWith(PREFIX) }
        if (keys.isEmpty()) return
        val editor = prefs(context).edit()
        extras.getString(PRESET)?.let { name ->
            val preset = presets.find { it.name == name } ?: error("Unknown preset $name, expected one of ${presets.map { it.name }}")
            preset.values.forEach { (key, value) -> editor.putBoolean(checkKey(key), value) }
        }
        for (key in keys) {
            if (key == PRESET) continue
            val name = checkKey(key.removePrefix(PREFIX))
            @Suppress("DEPRECATION")
            when (val value = extras.get(key)) {
                is Boolean -> editor.putBoolean(name, value)
                is Int -> editor.putInt(name, value)
                is String -> editor.putString(name, value)
            }
        }
        editor.commit()
    }

    private fun help(context: Context): String = buildString {
        appendLine()
        appendLine("Presets: --es debug.preset <name>")
        for (preset in presets) {
            appendLine("  ${preset.name}: ${preset.description}")
            appendLine("    ${preset.values.entries.joinToString { "${it.key}=${it.value}" }}")
        }
        appendLine("Settings: debug.<name>, single settings override the preset")
        val current = prefs(context).all
        for ((name, flag) in settings) {
            appendLine("  $flag debug.$name = ${current[name] ?: "unset"}")
        }
    }

    private fun checkKey(name: String): String {
        require(name in settings) { "Unknown setting $name, expected one of ${settings.keys}" }
        return name
    }

    private fun prefs(context: Context) = context.getSharedPreferences("unicode", Context.MODE_PRIVATE)

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private class Preset(val name: String, val description: String, val values: Map<String, Boolean>)

    private companion object {
        const val PREFIX = "debug."
        const val PRESET = "debug.preset"

        val settings: Map<String, String> = UserConfig::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("key_") && it.type == String::class.java }
            .associate { field ->
                field.isAccessible = true
                val name = field.get(null) as String
                val getter = UserConfig::class.java.methods.find { it.name == "get" + name.replaceFirstChar(Char::uppercase) }
                val flag = when (getter?.returnType) {
                    Boolean::class.javaPrimitiveType -> "--ez"
                    Int::class.javaPrimitiveType -> "--ei"
                    else -> "--es"
                }
                name to flag
            }
            .toSortedMap()

        val presets = listOf(
            Preset(
                "all", "every optional UI on",
                mapOf(
                    "topSearchEligible" to true,
                    "searchAtTop" to true,
                    "dockEnabled" to true,
                    "recentsEnabled" to true,
                    "mergedDetailsActions" to true,
                    "showUnsupportedChars" to true,
                ),
            ),
            Preset(
                "fresh", "state of a new install",
                mapOf(
                    "topSearchEligible" to false,
                    "dockEnabled" to true,
                    "centerTitle" to false,
                    "mergedDetailsActions" to true,
                ),
            ),
            Preset(
                "legacy", "state after an update from an old version, with the top search option",
                mapOf(
                    "topSearchEligible" to true,
                    "dockEnabled" to false,
                    "centerTitle" to true,
                    "mergedDetailsActions" to false,
                ),
            ),
            Preset(
                "tips", "first-use tips shown again",
                mapOf(
                    "usedCollect" to false,
                    "usedCopy" to false,
                    "usedPinchToZoom" to false,
                    "usedFastScroll" to false,
                ),
            ),
        )
    }
}
