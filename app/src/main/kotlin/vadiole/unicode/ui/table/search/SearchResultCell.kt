package vadiole.unicode.ui.table.search

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R

import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.ui.common.SimpleTextView
import vadiole.unicode.ui.common.StateColorDrawable

import vadiole.unicode.ui.common.roboto_regular
import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.onClick

class SearchResultCell(context: Context, delegate: Delegate) : FrameLayout(context) {

    interface Delegate {
        fun onClick(codePoint: CodePoint)
    }

    private val backgroundDrawable = StateColorDrawable()
    private var codePoint: CodePoint? = null
    val charView = SimpleTextView(context).apply {
        textSize = 24f.dp(context)
    }
    val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        typeface = (roboto_regular)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(0, 0, 16.dp(context), 0)
    }

    init {
        charView.textColor = this.context.getColor(R.color.windowTextPrimary)
        name.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        backgroundDrawable.colors = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_pressed)
            ),
            intArrayOf(
                this.context.getColor(R.color.windowSurfacePressed),
                this.context.getColor(R.color.windowSurface),
            ),
        )
        layoutParams = RecyclerView.LayoutParams(matchParent, 48.dp(context))
        addView(name, frameParams(matchParent, 48.dp(context), marginLeft = 64.dp(context)))
        addView(charView, frameParams(64.dp(context), 48.dp(context), gravity = Gravity.LEFT))
        background = backgroundDrawable
        isClickable = true
        isFocusable = true
        clipChildren = false
        onClick = {
            val codePoint = codePoint
            if (codePoint != null) {
                delegate.onClick(codePoint)
            }
        }
    }

    fun bind(data: SearchResult) {
        name.text = data.name
        charView.text = data.codePoint.char
        codePoint = data.codePoint
    }
}