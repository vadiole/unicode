package vadiole.unicode.ui.table.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.ui.common.CharTextView
import vadiole.unicode.ui.common.TEXT_CAPTION
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.font_regular
import vadiole.unicode.ui.common.rippleDrawable
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick
import vadiole.unicode.ui.extension.onLongClick
import java.util.Locale

class SearchResultCell(context: Context, delegate: Delegate) : FrameLayout(context) {

    interface Delegate {
        fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float)
        fun onLongClick(codePoint: CodePoint)
    }

    private val highlightColor = context.getColor(R.color.searchMatchHighlight)
    private val highlighter = SearchHighlighter(highlightColor)
    private var codePoint: CodePoint? = null
    var result: SearchResult? = null
        private set
    private val glyphSize = 24f.dp(context)
    val charView = CharTextView(context).apply {
        textSize = glyphSize
    }
    val name = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SECONDARY)
        typeface = font_regular
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }
    private val matchView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_CAPTION)
        typeface = font_regular
        includeFontPadding = false
        setTextColor(context.getColor(R.color.windowTextSecondary))
        visibility = GONE
    }
    private val texts = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 6.dp(context), 16.dp(context), 6.dp(context))
        addView(name, linearParams(matchParent, wrapContent))
        addView(matchView, linearParams(matchParent, wrapContent, marginTop = 2.dp(context)))
    }

    init {
        charView.textColor = this.context.getColor(R.color.windowTextPrimary)
        name.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent)
        minimumHeight = 48.dp(context)
        addView(texts, frameParams(matchParent, wrapContent, Gravity.CENTER_VERTICAL, marginLeft = 64.dp(context)))
        addView(charView, frameParams(64.dp(context), matchParent, gravity = Gravity.LEFT))
        background = context.rippleDrawable(
            content = ColorDrawable(context.getColor(R.color.windowSurface)),
            mask = ColorDrawable(Color.WHITE),
        )
        isClickable = true
        isFocusable = true
        clipChildren = false
        onClick = {
            val codePoint = codePoint
            if (codePoint != null) {
                delegate.onClick(codePoint, charView, charView.width / 2f, charView.height / 2f, glyphSize)
            }
        }
        onLongClick = {
            val codePoint = codePoint
            if (codePoint != null) {
                delegate.onLongClick(codePoint)
            }
        }
    }

    fun bind(data: SearchResult, abbreviations: Map<CodePoint, String>, tokens: Array<String>?) {
        val highlightedName = highlighter.highlight(data.name, tokens)
        name.text = if (data.codeMatchLength == 0) {
            highlightedName
        } else {
            appendCode(highlightedName, data.codePoint.value, data.codeMatchLength, data.codeMatchAtEnd)
        }
        charView.text = data.codePoint.char
        charView.abbreviation = abbreviations[data.codePoint]
        codePoint = data.codePoint
        result = data
        bindMatch(null)
    }

    fun bindMatch(match: CharSequence?) {
        matchView.text = match
        matchView.visibility = if (match == null) GONE else VISIBLE
    }

    private fun appendCode(name: CharSequence, codePoint: Int, matchLength: Int, matchAtEnd: Boolean): CharSequence {
        val hex = Integer.toHexString(codePoint).uppercase(Locale.ROOT).padStart(4, '0')
        val builder = SpannableStringBuilder(name).append(" [U+").append(hex).append(']')
        val hexEnd = builder.length - 1
        val length = minOf(matchLength, hex.length)
        val start = if (matchAtEnd) hexEnd - length else hexEnd - hex.length
        builder.setSpan(BackgroundColorSpan(highlightColor), start, start + length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return builder
    }
}
