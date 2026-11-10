package vadiole.unicode.ui.table.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
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

class SearchResultCell(context: Context, private val matchInline: Boolean, delegate: Delegate) : FrameLayout(context) {

    interface Delegate {
        fun onClick(codePoint: CodePoint, source: View, centerX: Float, centerY: Float, glyphSize: Float)
        fun onLongClick(codePoint: CodePoint)
    }

    private val highlightColor = context.getColor(R.color.searchMatchHighlight)
    private val matchColor = context.getColor(R.color.windowTextSecondary)
    private var codePoint: CodePoint? = null
    private var title: CharSequence = ""
    var result: SearchResult? = null
        private set
    private var highlightStarts = IntArray(4)
    private var highlightEnds = IntArray(4)
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
        setTextColor(matchColor)
        visibility = GONE
    }
    private val texts = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 6.dp(context), 16.dp(context), 6.dp(context))
        addView(name, linearParams(matchParent, wrapContent))
        if (!matchInline) addView(matchView, linearParams(matchParent, wrapContent, marginTop = 2.dp(context)))
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
        val highlightedName = buildHighlightedName(data.name, tokens)
        title = if (data.codeMatchLength == 0) {
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

    fun bindMatch(match: String?) {
        if (matchInline) bindMatchInline(match) else bindMatchBelow(match)
    }

    private fun bindMatchInline(match: String?) {
        if (match == null) {
            name.text = title
            name.contentDescription = null
        } else {
            val text = SpannableStringBuilder(title).append(MATCH_SEPARATOR)
            val start = text.length
            text.append(match).setSpan(ForegroundColorSpan(matchColor), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            name.text = text
            name.contentDescription = "$title, $match"
        }
    }

    private fun bindMatchBelow(match: String?) {
        name.text = title
        matchView.text = match
        matchView.visibility = if (match == null) GONE else VISIBLE
    }

    private companion object {
        const val MATCH_SEPARATOR = " · "
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

    private fun buildHighlightedName(source: String, tokens: Array<String>?): CharSequence {
        if (tokens == null || tokens.isEmpty()) return source
        if (highlightStarts.size < tokens.size) {
            highlightStarts = IntArray(tokens.size)
            highlightEnds = IntArray(tokens.size)
        }
        val starts = highlightStarts
        val ends = highlightEnds
        var count = 0
        for (token in tokens) {
            val len = token.length
            if (len == 0) continue
            var bestPriority = 7
            var bestStart = -1
            var pos = source.indexOf(token)
            while (pos >= 0) {
                val p = priorityOf(source, pos, len)
                if (p < bestPriority) {
                    bestPriority = p
                    bestStart = pos
                    if (p == 1) break
                }
                pos = source.indexOf(token, pos + 1)
            }
            if (bestStart >= 0) {
                starts[count] = bestStart
                ends[count] = bestStart + len
                count++
            }
        }
        if (count == 0) return source

        for (i in 1 until count) {
            val s = starts[i]
            val e = ends[i]
            var j = i - 1
            while (j >= 0 && starts[j] > s) {
                starts[j + 1] = starts[j]
                ends[j + 1] = ends[j]
                j--
            }
            starts[j + 1] = s
            ends[j + 1] = e
        }
        var w = 0
        for (i in 1 until count) {
            if (starts[i] <= ends[w]) {
                if (ends[i] > ends[w]) ends[w] = ends[i]
            } else {
                w++
                starts[w] = starts[i]
                ends[w] = ends[i]
            }
        }
        val finalCount = w + 1

        val spannable = SpannableString(source)
        for (i in 0 until finalCount) {
            spannable.setSpan(BackgroundColorSpan(highlightColor), starts[i], ends[i], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun priorityOf(name: String, pos: Int, len: Int): Int {
        val end = pos + len
        val atStart = pos == 0
        val atEnd = end == name.length
        if (atStart && atEnd) return 1
        val beforeIsSpace = !atStart && name[pos - 1] == ' '
        val afterIsSpace = !atEnd && name[end] == ' '
        if (atEnd && beforeIsSpace) return 2
        if (atStart && afterIsSpace) return 3
        if (atStart) return 4
        if (beforeIsSpace && afterIsSpace) return 5
        return 6
    }
}