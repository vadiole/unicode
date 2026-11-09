package vadiole.unicode

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.updatePadding
import vadiole.unicode.ui.common.TEXT_TITLE
import vadiole.unicode.ui.common.font_semibold
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.insetsController
import vadiole.unicode.ui.common.isDarkMode
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.wrapContent

class PrototypeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.layer0))
            addView(TextView(context).apply {
                text = "Prototype"
                textSize = TEXT_TITLE
                typeface = font_semibold
                setTextColor(getColor(R.color.windowTextPrimary))
            }, frameParams(wrapContent, wrapContent, Gravity.CENTER))
            setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(top = insets.statusBars.top, bottom = insets.navigationBars.bottom)
                insets
            }
        }, frameParams(matchParent, matchParent))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) return
        val dark = resources.configuration.isDarkMode
        insetsController.isAppearanceLightStatusBars = !dark
        insetsController.isAppearanceLightNavigationBars = !dark
    }
}
