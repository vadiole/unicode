package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp
import vadiole.unicode.data.CharStorage
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.extension.insetsController
import vadiole.unicode.utils.extension.isDarkMode

class UnicodeActivity : Activity() {
    private var backHandler: () -> Boolean = { false }
    private var deepLinkHandler: (id: Int) -> Unit = { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val appComponent = (applicationContext as UnicodeApp).appComponent
        val navigationView = NavigationView(this, appComponent)
        setContentView(navigationView)
        backHandler = {
            navigationView.hideDetailsBottomSheet()
        }
        deepLinkHandler = { id ->
            navigationView.showDetailsBottomSheet(id, skipAnimation = true)
        }
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        val charId = intent.data?.getQueryParameter("c")?.toIntOrNull() ?: return
        if (charId >= 1 && charId < CharStorage.totalCharacters) {
            deepLinkHandler.invoke(charId)
        }
    }

    override fun onBackPressed() {
        val handled = backHandler.invoke()
        if (!handled) {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDarkMode = newConfig.isDarkMode
        val appComponent = (applicationContext as UnicodeApp).appComponent
        val themeManager = appComponent.theme
        val scheme = if (isDarkMode) AppTheme.Scheme.BLUE_DARK else AppTheme.Scheme.BLUE_LIGHT
        themeManager.applyScheme(scheme)
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
}