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

import vadiole.unicode.ui.common.roboto_regular
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.matchParent

class BlockSelectorCell(context: Context) : TextView(context) {

    private val backgroundDrawable = StateColorDrawable()

    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, 48.dp(context))
        applyTheme()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
        typeface = roboto_regular
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(16.dp(context), 0, 16.dp(context), 0)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        background = backgroundDrawable
    }

    fun applyTheme() {
        setTextColor(context.getColor(R.color.windowTextPrimary))
        backgroundDrawable.colors = ColorStateList(
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
}
