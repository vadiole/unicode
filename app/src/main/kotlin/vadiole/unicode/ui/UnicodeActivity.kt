package vadiole.unicode.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp
import vadiole.unicode.ui.components.NavigationView
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.insetsController
import vadiole.unicode.utils.isDarkMode

class UnicodeActivity : Activity() {
    private var backHandler: () -> Boolean = { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val appComponent = (applicationContext as UnicodeApp).appComponent
        val navigationView = NavigationView(this, appComponent)
        setContentView(navigationView)
        backHandler = {
            navigationView.hideDetailsBottomSheet()
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