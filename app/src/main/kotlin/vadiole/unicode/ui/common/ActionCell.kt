package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.DrawableRes
import vadiole.unicode.R

class ActionCell(
    context: Context,
    name: String,
    private val topItem: Boolean = false,
    private val bottomItem: Boolean = false,
) : TextView(context) {
    private val backgroundDrawable = run {
        val r = 13.dp(context)
        when {
            topItem -> SquircleDrawable(cornerRadius = 0, topLeftRadius = r, topRightRadius = r)
            bottomItem -> SquircleDrawable(cornerRadius = 0, bottomRightRadius = r, bottomLeftRadius = r)
            else -> SquircleDrawable(r)
        }
    }

    init {
        backgroundDrawable.colors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_pressed),
            ),
            intArrayOf(
                this.context.getColor(R.color.layer1Pressed),
                this.context.getColor(R.color.layer1),
            )
        )
        compoundDrawableTintList = this.context.getColorStateList(R.color.windowTextPrimary)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        background = backgroundDrawable
        text = name
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        minHeight = 48.dp(context)
        includeFontPadding = false
        typeface = mono_regular
        textSize = TEXT_BODY
        isClickable = true
        isFocusable = true
        setPadding(16.dp(context), 10.dp(context), 16.dp(context), 10.dp(context))
    }

    fun setIcon(@DrawableRes iconId: Int) {
        setCompoundDrawablesWithIntrinsicBounds(0, 0, iconId, 0)
    }

}