package vadiole.unicode.ui.table.search

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.fontRegular
import vadiole.unicode.ui.common.wrapContent

class SearchHeaderCell(context: Context) : TextView(context) {
    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
        minHeight = 40.dp(context)
        setPadding(16.dp(context), 8.dp(context), 16.dp(context), 8.dp(context))
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        typeface = fontRegular
        textSize = TEXT_SECONDARY
        setTextColor(context.getColor(R.color.windowTextSecondary))
        text = context.getString(R.string.search_recents)
        ViewCompat.setAccessibilityHeading(this, true)
    }
}
