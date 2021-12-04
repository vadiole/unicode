package vadiole.unicode.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp
import vadiole.unicode.ui.screen.table.TableController
import vadiole.unicode.ui.screen.table.TableScreen
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.insetsController
import vadiole.unicode.utils.isDarkMode

class UnicodeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val appComponent = (applicationContext as UnicodeApp).appComponent

        val charStorage = appComponent.charsStorage
        val theme = appComponent.theme
        val tableViewModel = TableController(charStorage)
        val table = TableScreen(this, theme, tableViewModel)

        setContentView(table)
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