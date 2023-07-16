package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.theme.blue_dark
import vadiole.unicode.ui.theme.blue_light
import vadiole.unicode.utils.extension.insetsController
import vadiole.unicode.utils.extension.isDarkMode

class UnicodeActivity : Activity() {
    private var backButtonHandler: () -> Boolean = { false }
    private var deepLinkHandler: (codePoint: CodePoint) -> Unit = { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val navigationView = NavigationView(this)
        setContentView(navigationView)
        backButtonHandler = navigationView::onBackPressed

        deepLinkHandler = { codePoint ->
            navigationView.showDetailsBottomSheet(codePoint, skipAnimation = true)
        }
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        val codePointValue = intent.data?.getQueryParameter("c")?.toIntOrNull() ?: return
        if (codePointValue in 1..917999) {
            val codePoint = CodePoint(codePointValue)
            deepLinkHandler.invoke(codePoint)
        }
    }

    override fun onBackPressed() {
        val handled = backButtonHandler.invoke()
        if (!handled) {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDarkMode = newConfig.isDarkMode
        val colors = if (isDarkMode) blue_dark else blue_light
        themeManager.setThemeColors(colors)
        updateSystemBars(isDarkMode)
        window.decorView.setBackgroundColor(getColor(R.color.windowBackground))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            updateSystemBars(resources.configuration.isDarkMode)
        }
    }


    private fun updateSystemBars(isDarkMode: Boolean) {
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
    }

    override fun onDestroy() {
        super.onDestroy()
        backButtonHandler = { false }
        deepLinkHandler = { _ -> }
    }
}
