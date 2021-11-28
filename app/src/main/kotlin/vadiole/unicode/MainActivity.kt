package vadiole.unicode

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {

    private val insetsControllerX: WindowInsetsControllerCompat?
        get() = WindowCompat.getInsetsController(window, window.decorView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(
                0, insets.getInsets(statusBars()).top,
                0, insets.getInsets(navigationBars()).bottom
            )
            insets
        }

        setContentView(
            TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(fill, fill)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f.dpf(context))
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                text = "Unicode by vadiole"
            }
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars(newConfig)
        window.decorView.setBackgroundColor(getColor(R.color.windowBackground))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        updateSystemBars(resources.configuration)
    }

    private fun updateSystemBars(configuration: Configuration) {
        val isDarkMode = SDK_INT >= 30 && configuration.isNightModeActive
        insetsControllerX?.isAppearanceLightStatusBars = !isDarkMode
        insetsControllerX?.isAppearanceLightNavigationBars = !isDarkMode
    }
}