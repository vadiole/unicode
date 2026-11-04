package vadiole.unicode.ui.table.search

import android.graphics.Paint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.RecentRepository
import vadiole.unicode.data.config.UserConfig

class SearchController(
    private val unicodeStorage: UnicodeStorage,
    private val recentRepository: RecentRepository,
    private val userConfig: UserConfig,
) {
    private val glyphPaint = Paint()
    private val acceptGlyph: (String) -> Boolean = { userConfig.showUnsupportedChars || glyphPaint.hasGlyph(it) }
    var searchResult: Array<SearchResult> = emptyArray()
    var recentResult: Array<SearchResult> = emptyArray()
    var queryTokens: Array<String> = emptyArray()

    suspend fun loadRecents() {
        val codePoints = recentRepository.getRecents()
        if (codePoints.isEmpty()) {
            recentResult = emptyArray()
            return
        }
        val resultMap = unicodeStorage.findCharsByCodePoints(codePoints, acceptGlyph)
        val results = ArrayList<SearchResult>(resultMap.size)
        for (cp in codePoints) {
            val result = resultMap[cp.value]
            if (result != null) {
                results.add(result)
            }
        }
        recentResult = results.toTypedArray()
    }

    suspend fun search(query: String, count: Int = -1) {
        if (query.isEmpty()) {
            queryTokens = emptyArray()
            searchResult = emptyArray()
            return
        }

        val trimmed = query.trim()
        val codeQuery = parseCodeQuery(trimmed)
        if (codeQuery != null && codeQuery.explicit) {
            queryTokens = emptyArray()
            searchResult = findByCode(codeQuery, count)
            return
        }

        val results = mutableListOf<SearchResult>()
        var directCodePoint = -1
        if (codeQuery != null) {
            results.addAll(findByCode(codeQuery, count))
        } else {
            directCodePoint = tryParseCharLiteral(query) ?: -1
            if (directCodePoint >= 0) {
                val direct = unicodeStorage.findCharByCodePoint(directCodePoint, acceptGlyph)
                if (direct != null) {
                    results.add(direct)
                }
            }
        }

        if (trimmed.isNotEmpty()) {
            val tokens = whitespaceRegex.split(trimmed)
            val nameResults = if (tokens.size == 1) {
                unicodeStorage.findCharsByName(tokens[0], count, acceptGlyph)
            } else {
                unicodeStorage.findCharsByNameMultiWord(tokens, count, acceptGlyph)
            }

            for (r in nameResults) {
                val cp = r.codePoint.value
                val duplicate = codeQuery?.matches(cp) ?: (cp == directCodePoint)
                if (!duplicate) {
                    results.add(r)
                }
            }
            val unique = LinkedHashSet<String>(tokens.size)
            for (t in tokens) unique.add(t.uppercase(java.util.Locale.ROOT))
            queryTokens = unique.toTypedArray()
        } else {
            queryTokens = emptyArray()
        }

        searchResult = results.toTypedArray()
    }

    private suspend fun findByCode(query: CodeQuery, count: Int): Array<SearchResult> {
        return unicodeStorage.findCharsByCodePointPrefix(query.exact, query.prefixLength, query.ranges, count, acceptGlyph)
    }

    private fun parseCodeQuery(input: String): CodeQuery? {
        val explicit = input.startsWith("U+", ignoreCase = true) ||
                input.startsWith("0x", ignoreCase = true) ||
                input.startsWith("\\u", ignoreCase = true)
        val digits = when {
            explicit -> input.substring(2)
            input.startsWith("u", ignoreCase = true) -> input.substring(1)
            input.length >= 2 -> input
            else -> return null
        }
        if (digits.isEmpty() || digits.length > 8) return null
        var value = 0
        var hasDigit = false
        for (c in digits) {
            val d = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> return null
            }
            if (d < 10) hasDigit = true
            value = value * 16 + d
            if (value > MAX_CODE_POINT) return null
        }
        if (!explicit && !hasDigit) return null

        val ranges = IntArray(6)
        var rangeCount = 0
        val leadingZero = digits[0] == '0'
        for (width in maxOf(4, digits.length + 1)..6) {
            if (width > 4 && leadingZero) break
            val shift = 4 * (width - digits.length)
            val lo = value.toLong() shl shift
            if (lo > MAX_CODE_POINT) break
            val hi = minOf(lo + (1L shl shift) - 1, MAX_CODE_POINT.toLong())
            ranges[rangeCount * 2] = lo.toInt()
            ranges[rangeCount * 2 + 1] = hi.toInt()
            rangeCount++
        }
        return CodeQuery(explicit, value, digits.length, ranges.copyOf(rangeCount * 2))
    }

    private class CodeQuery(val explicit: Boolean, val exact: Int, val prefixLength: Int, val ranges: IntArray) {
        fun matches(cp: Int): Boolean {
            if (cp == exact) return true
            for (i in ranges.indices step 2) {
                if (cp >= ranges[i] && cp <= ranges[i + 1]) return true
            }
            return false
        }
    }

    private fun tryParseCharLiteral(input: String): Int? {
        if (input.length > 2) return null
        if (input.codePointCount(0, input.length) != 1) return null
        return input.codePointAt(0)
    }

    companion object {
        private val whitespaceRegex = "\\s+".toRegex()
        private const val MAX_CODE_POINT = 0x10FFFF
    }
}
