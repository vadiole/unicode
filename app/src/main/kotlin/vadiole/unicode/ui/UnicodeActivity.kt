package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import vadiole.unicode.data.CodePoint
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

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "Test crash", Toast.LENGTH_SHORT).show()
            error("Test error")
        }, 2000)
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
