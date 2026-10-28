package vadiole.unicode.ui.table.selector

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.ui.common.StateColorDrawable
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.mono_regular
import vadiole.unicode.ui.common.wrapContent

class BlockSelectorCell(context: Context) : TextView(context) {

    private val backgroundDrawable = StateColorDrawable().apply {
        colors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_pressed),
            ),
            intArrayOf(
                context.getColor(R.color.windowSurfacePressed),
                context.getColor(R.color.windowSurface),
            )
        )
    }

    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
        minHeight = 48.dp(context)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        typeface = mono_regular
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(16.dp(context), 4.dp(context), 16.dp(context), 4.dp(context))
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        background = backgroundDrawable
    }
}