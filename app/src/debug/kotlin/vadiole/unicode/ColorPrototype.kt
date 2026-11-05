package vadiole.unicode

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import vadiole.unicode.ui.UnicodeActivity

object ColorPrototype {
    enum class Fix(val label: Int) {
        ABBREVIATIONS(R.string.prototype_abbreviations),
        SCROLLBAR(R.string.prototype_scrollbar),
        SECONDARY(R.string.prototype_secondary),
        ACCENT(R.string.prototype_accent),
        BORDERS(R.string.prototype_borders),
    }

    var fixes: Set<Fix> = emptySet()
        private set
    var nightMode: Int? = null
        private set
    var expanded = true
    var panelX = -1
    var panelY = -1
    private var snapshot: ColorPrototypeSnapshot? = null

    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        nightMode?.let { configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or it }
        val configured = context.createConfigurationContext(configuration)
        val colors = PrototypeResources(configured.resources, fixes.toSet())
        return object : ContextThemeWrapper(configured, R.style.Theme_Unicode) {
            override fun getResources(): Resources = colors
        }
    }

    fun onCreated(activity: Activity) {
        if (activity !is UnicodeActivity) return
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (!dark && Fix.ACCENT in fixes) activity.theme.applyStyle(R.style.PrototypeAccent, true)
        if (snapshot != null) {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    fun attach(activity: Activity) {
        if (activity !is UnicodeActivity) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content.findViewById<android.view.View>(R.id.prototype_overlay) != null) return
        val saved = snapshot
        snapshot = null
        content.post {
            if (activity.isDestroyed) return@post
            saved?.restore(activity)
            ColorPrototypeOverlay(activity).attach(content)
        }
    }

    fun select(activity: Activity, selected: Set<Fix> = fixes, mode: Int? = nightMode) {
        if (selected == fixes && mode == nightMode) return
        snapshot = ColorPrototypeSnapshot.capture(activity)
        fixes = selected.toSet()
        nightMode = mode
        activity.window.setWindowAnimations(0)
        activity.recreate()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class PrototypeResources(private val original: Resources, private val enabled: Set<Fix>) :
        Resources(original.assets, original.displayMetrics, original.configuration) {
        private val dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        override fun getConfiguration(): Configuration = original.configuration

        override fun getColor(id: Int): Int = getColor(id, null)

        override fun getColor(id: Int, theme: Theme?): Int {
            return when {
                id == R.color.characterAbbreviation && Fix.ABBREVIATIONS in enabled -> getColor(R.color.windowTextSecondary, theme)
                id == R.color.windowTextSecondary && Fix.SECONDARY in enabled && !dark -> 0xFF636367.toInt()
                id == R.color.scrollIndicator && Fix.SCROLLBAR in enabled -> 0xFF808084.toInt()
                id == R.color.scrollIndicatorActive && Fix.SCROLLBAR in enabled -> if (dark) 0xFF909094.toInt() else 0xFF707074.toInt()
                id == R.color.scrollIndicatorGrip && Fix.SCROLLBAR in enabled -> if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                id == R.color.colorPrimary && Fix.ACCENT in enabled && !dark -> 0xFF3A86E0.toInt()
                id == R.color.floatingBorder && Fix.BORDERS in enabled -> if (dark) 0x26FFFFFF else 0x403C3C43
                else -> original.getColor(id, theme)
            }
        }

        override fun getColorStateList(id: Int): ColorStateList = getColorStateList(id, null)

        override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
            return if (id in palette) ColorStateList.valueOf(getColor(id, theme)) else original.getColorStateList(id, theme)
        }

        private val palette = setOf(
            R.color.characterAbbreviation,
            R.color.windowTextSecondary,
            R.color.scrollIndicator,
            R.color.scrollIndicatorActive,
            R.color.scrollIndicatorGrip,
            R.color.colorPrimary,
            R.color.floatingBorder,
        )
    }
}
