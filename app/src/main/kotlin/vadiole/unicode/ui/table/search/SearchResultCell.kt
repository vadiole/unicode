package vadiole.unicode.ui.table.search

import android.content.Context
import android.content.res.ColorStateList
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vadiole.unicode.R
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.ui.common.CharTextView
import vadiole.unicode.ui.common.StateColorDrawable
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.roboto_regular
import vadiole.unicode.ui.extension.onClick

class SearchResultCell(context: Context, delegate: Delegate) : FrameLayout(context) {

    interface Delegate {
        fun onClick(codePoint: CodePoint)
    }

    private val backgroundDrawable = StateColorDrawable()
    private val highlightColor = context.getColor(R.color.searchMatchHighlight)
    private var codePoint: CodePoint? = null
    private var highlightStarts = IntArray(4)
    private var highlightEnds = IntArray(4)
    val charView = CharTextView(context).apply {
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
                this.context.getColor(R.color.windowSurface),
                this.context.getColor(R.color.windowSurfacePressed),
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

    fun bind(data: SearchResult, abbreviations: Map<CodePoint, String>, tokens: Array<String>?) {
        name.text = buildHighlightedName(data.name, tokens)
        charView.text = data.codePoint.char
        charView.abbreviation = abbreviations[data.codePoint]
        codePoint = data.codePoint
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