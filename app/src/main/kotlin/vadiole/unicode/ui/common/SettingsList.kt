package vadiole.unicode.ui.common

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updatePadding
import vadiole.unicode.R

class SettingsList(context: Context) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val bottomPadding = 24.dp(context)

    init {
        isFillViewport = true
        clipToPadding = false
        addView(content, frameParams(matchParent, wrapContent))
        setOnApplyWindowInsetsListener(this) { _, insets ->
            content.updatePadding(bottom = bottomPadding + insets.navigationBars.bottom)
            insets
        }
    }

    fun add(view: View, marginTop: Int = 16.dp(context), gravity: Int = Gravity.NO_GRAVITY, width: Int = matchParent, height: Int = wrapContent) {
        val margin = 16.dp(context)
        content.addView(view, linearParams(width, height, gravity, marginLeft = margin, marginTop = marginTop, marginRight = margin))
    }

    fun addFootnote(text: String) {
        add(TextView(context).apply {
            this.text = text
            textSize = TEXT_SECONDARY
            typeface = font_regular
            includeFontPadding = false
            setTextColor(context.getColor(R.color.windowTextSecondary))
            setPadding(16.dp(context), 0, 16.dp(context), 0)
        }, marginTop = 8.dp(context))
    }
}
