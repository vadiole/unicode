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
    private val chevron = context.getDrawable(R.drawable.ic_chevron_down)!!.mutate().apply {
        setTint(context.getColor(R.color.windowTextSecondary))
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }
    val titleView = TextView(context).apply {
        setLineHeightX(22.dp(context))
        typeface = roboto_semibold
        includeFontPadding = false
        gravity = Gravity.CENTER
        onClick = onTitleClick
        letterSpacing = 0.03f
        setPadding(44.dp(context), 0, 44.dp(context), 0)
        compoundDrawablePadding = 4.dp(context)
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, MAX_TITLE_SP.toFloat())
        text = title
        setCompoundDrawablesRelative(null, null, chevron, null)
    }

    init {
        setBackgroundColor(this.context.getColor(R.color.topBarBackground))
        titleView.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        addView(titleView, frameParams(wrapContent, 42.dp(context), gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    fun setTitle(text: String) {
        if (titleView.text.toString() == text) return
        titleView.text = text
        fitTitle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitTitle()
    }

    private fun fitTitle() {
        val available = width - 2 * 44.dp(context) - chevron.intrinsicWidth - titleView.compoundDrawablePadding
        if (available <= 0) return
        val text = titleView.text.toString()
        val paint = titleView.paint
        val metrics = resources.displayMetrics
        var fittingSp = MIN_TITLE_SP
        for (sizeSp in MAX_TITLE_SP downTo MIN_TITLE_SP) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), metrics)
            if (paint.measureText(text) <= available) {
                fittingSp = sizeSp
                break
            }
        }
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fittingSp.toFloat())
    }

    companion object {
        private const val MIN_TITLE_SP = 9
        private const val MAX_TITLE_SP = 17
    }
}
