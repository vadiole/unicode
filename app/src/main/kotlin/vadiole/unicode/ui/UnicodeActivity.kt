package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.AppScope
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.ui.theme.ThemeOwner
import vadiole.unicode.util.extension.insetsController
import vadiole.unicode.util.extension.isDarkMode

class UnicodeActivity : Activity(), AppScope {
    private var backButtonHandler: () -> Boolean = { false }
    private var deepLinkHandler: (codePoint: CodePoint) -> Unit = { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val navigationView = NavigationView(this, appComponent)
        setContentView(navigationView)
        backButtonHandler = {
            navigationView.hideDetailsBottomSheet()
        }
        deepLinkHandler = { codePoint ->
            navigationView.showDetailsBottomSheet(codePoint, skipAnimation = true)
        }
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        val codePointValue = intent.data?.getQueryParameter("c")?.toIntOrNull() ?: return
        if (codePointValue >= 1 && codePointValue < UnicodeStorage.totalCharacters) {
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
        val colors = if (isDarkMode) AppTheme.blueDark else AppTheme.blueLight
        theme.applyColors(colors)
        window.decorView.setBackgroundColor(getColor(R.color.windowBackground))
        updateSystemBars(isDarkMode)
        requestThemeInvalidate(window.decorView as ViewGroup)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            updateSystemBars(resources.configuration.isDarkMode)
        }
    }

    private fun requestThemeInvalidate(group: ViewGroup) {
        for (index in 0 until group.childCount) {
            val view = group.getChildAt(index)
            if (view is ThemeOwner) {
                view.invalidateTheme()
            }
            if (view is ViewGroup) {
                requestThemeInvalidate(view)
            }
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