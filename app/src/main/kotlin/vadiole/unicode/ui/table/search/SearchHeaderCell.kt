package vadiole.unicode.ui.table.search

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.roboto_regular

class SearchHeaderCell(context: Context) : FrameLayout(context) {

    private val titleView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        typeface = roboto_regular
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setTextColor(this@SearchHeaderCell.context.getColor(R.color.windowTextSecondary))
        text = "Recents"
    }

    init {
        layoutParams = RecyclerView.LayoutParams(matchParent, 40.dp(context))
        addView(titleView, frameParams(matchParent, 40.dp(context), marginLeft = 14.dp(context)))
    }
}
