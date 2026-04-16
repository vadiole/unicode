package vadiole.unicode.ui.common

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import vadiole.unicode.R
import vadiole.unicode.ui.extension.onClick

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
        setPadding(24.dp(context), 0, 24.dp(context), 0)
        compoundDrawablePadding = 4.dp(context)
        val chevron = context.getDrawable(R.drawable.ic_chevron_down)!!.mutate().apply {
            setTint(context.getColor(R.color.windowTextSecondary))
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        }
        setCompoundDrawablesRelative(null, null, chevron, null)
    }

    init {
        setBackgroundColor(this.context.getColor(R.color.topBarBackground))
        titleView.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        addView(titleView, frameParams(wrapContent, 42.dp(context), gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    fun setTitle(text: String) {
        titleView.text = text
    }
}
