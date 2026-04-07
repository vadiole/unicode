package vadiole.unicode.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.common.insetsController
import vadiole.unicode.ui.common.isDarkMode

class UnicodeActivity : Activity() {
    private var navigationViewNullable: NavigationView? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var isResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        val navigationView = NavigationView(this)
        navigationViewNullable = navigationView
        setContentView(navigationView)
        onNewIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                object : OnBackAnimationCallback {
                    override fun onBackInvoked() = navigationView.onBackInvoked()

                    override fun onBackStarted(backEvent: BackEvent) = navigationView.onBackStarted(backEvent)

                    override fun onBackProgressed(backEvent: BackEvent) = navigationView.onBackProgressed(backEvent)

                    override fun onBackCancelled() = navigationView.onBackCancelled()
                }
            } else {
                object : OnBackInvokedCallback {
                    override fun onBackInvoked() = navigationView.onBackInvoked()
                }
            }

            coroutineScope.launch {
                navigationView.isBackEnabled.collect { isEnabled ->
                    if (isEnabled) {
                        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                    } else {
                        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        val navigationView = navigationViewNullable ?: return
        navigationView.post {
            val codePointValue = intent.data?.getQueryParameter("c")?.toIntOrNull()
            if (codePointValue != null && codePointValue in 1..917999) {
                val codePoint = CodePoint(codePointValue)
                navigationView.showDetailsBottomSheet(codePoint, skipAnimation = true)
            }
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.onBackPressed()
        } else {
            val navigationView = navigationViewNullable ?: return
            if (navigationView.isBackEnabled.value) {
                navigationView.onBackInvoked()
            } else {
                super.onBackPressed()
            }
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

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationViewNullable = null
        coroutineScope.cancel()
    }
}