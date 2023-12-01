package vadiole.unicode.ui.common

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import vadiole.unicode.R


import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.onClick
import vadiole.unicode.utils.extension.setLineHeightX

class TopBar(
    context: Context,
    title: String,
    onTitleClick: TextView.() -> Unit = {},
) : FrameLayout(context) {
    val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
        setLineHeightX(22.dp(context))
        typeface = roboto_semibold
        includeFontPadding = false
        gravity = Gravity.CENTER
        onClick = onTitleClick
        letterSpacing = 0.03f
        text = title
    }

    init {
        setBackgroundColor(this.context.getColor(R.color.topBarBackground))
        titleView.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        addView(titleView, frameParams(matchParent, 42.dp(context), gravity = Gravity.BOTTOM))
    }

    fun setTitle(text: String) {
        titleView.text = text
    }
}