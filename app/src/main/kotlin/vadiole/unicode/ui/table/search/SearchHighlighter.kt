package vadiole.unicode.ui.table.search

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import vadiole.unicode.search.SearchTokens

class SearchHighlighter(private val color: Int) {
    private val folded = StringBuilder()
    private var foldedStart = IntArray(64)
    private var foldedEnd = IntArray(64)
    private var starts = IntArray(4)
    private var ends = IntArray(4)

    fun highlight(source: String, tokens: Array<String>?): CharSequence {
        if (tokens == null || tokens.isEmpty()) return source
        fold(source)
        if (starts.size < tokens.size) {
            starts = IntArray(tokens.size)
            ends = IntArray(tokens.size)
        }
        val starts = starts
        val ends = ends
        var count = 0
        for (token in tokens) {
            val len = token.length
            if (len == 0) continue
            var bestPriority = if (len >= MIN_INSIDE || token[0].code >= 0x80) 8 else 7
            var bestStart = -1
            var pos = folded.indexOf(token)
            while (pos >= 0) {
                val p = priorityOf(pos, len)
                if (p < bestPriority) {
                    bestPriority = p
                    bestStart = pos
                    if (p == 1) break
                }
                pos = folded.indexOf(token, pos + 1)
            }
            if (bestStart >= 0) {
                starts[count] = foldedStart[bestStart]
                ends[count] = foldedEnd[bestStart + len - 1]
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
        val spannable = SpannableString(source)
        for (i in 0..w) {
            spannable.setSpan(BackgroundColorSpan(color), starts[i], ends[i], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun fold(source: String) {
        folded.setLength(0)
        if (foldedStart.size < source.length * 2) {
            foldedStart = IntArray(source.length * 2)
            foldedEnd = IntArray(source.length * 2)
        }
        var i = 0
        while (i < source.length) {
            val cp = source.codePointAt(i)
            val end = i + Character.charCount(cp)
            if (SearchTokens.typeOf(cp) != SearchTokens.MARK || cp in SearchTokens.SCRIPT_MARK_FIRST..SearchTokens.SCRIPT_MARK_LAST) {
                val value = SearchTokens.fold(cp)
                val from = folded.length
                folded.appendCodePoint(value and SearchTokens.FIRST_MASK)
                if (value ushr SearchTokens.SECOND_SHIFT != 0) folded.appendCodePoint(value ushr SearchTokens.SECOND_SHIFT)
                for (k in from until folded.length) {
                    foldedStart[k] = i
                    foldedEnd[k] = end
                }
            } else if (folded.isNotEmpty()) {
                foldedEnd[folded.length - 1] = end
            }
            i = end
        }
    }

    private fun priorityOf(pos: Int, len: Int): Int {
        val end = pos + len
        val atStart = pos == 0
        val atEnd = end == folded.length
        if (atStart && atEnd) return 1
        val wordStart = atStart || !isWord(folded[pos - 1])
        val wordEnd = atEnd || !isWord(folded[end])
        if (!wordStart) return 7
        if (atEnd) return 2
        if (atStart && wordEnd) return 3
        if (atStart) return 4
        if (wordEnd) return 5
        return 6
    }

    private fun isWord(char: Char): Boolean {
        val type = SearchTokens.typeOf(char.code)
        return type == SearchTokens.WORD || type == SearchTokens.MARK
    }

    private companion object {
        const val MIN_INSIDE = 3
    }
}
