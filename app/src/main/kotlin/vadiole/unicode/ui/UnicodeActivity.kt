package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.ui.common.ThemeOwner
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.extension.forEach
import vadiole.unicode.utils.extension.insetsController
import vadiole.unicode.utils.extension.isDarkMode

class UnicodeActivity : Activity() {
    private var backButtonHandler: () -> Boolean = { false }
    private var deepLinkHandler: (codePoint: CodePoint) -> Unit = { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val appComponent = (applicationContext as UnicodeApp).appComponent
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
        val appComponent = (applicationContext as UnicodeApp).appComponent
        val colors = if (isDarkMode) AppTheme.blueDark else AppTheme.blueLight
        appComponent.theme.applyColors(colors)
        updateSystemBars(isDarkMode)
        applyTheme(window.decorView as ViewGroup)
        window.decorView.setBackgroundColor(getColor(R.color.windowBackground))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            updateSystemBars(resources.configuration.isDarkMode)
        }
    }

    fun applyTheme(group: ViewGroup) {
        group.forEach { child ->
            if (child is ThemeOwner) {
                child.invalidateTheme()
            }
            if (child is ViewGroup) {
                applyTheme(child)
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