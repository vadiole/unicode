package vadiole.unicode.ui.table.search

import android.graphics.Paint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.UnicodeStorage

class SearchController(private val unicodeStorage: UnicodeStorage) {
    private val glyphPaint = Paint()
    private val hasGlyph: (String) -> Boolean = glyphPaint::hasGlyph
    var searchResult: Array<SearchResult> = emptyArray()

    suspend fun search(query: String, count: Int = -1) {
        if (query.isEmpty()) {
            searchResult = emptyArray()
            return
        }

        val results = mutableListOf<SearchResult>()

        val directCodePoint = tryParseCharLiteral(query)
        val trimmed = query.trim()
        val hexCodePoint = tryParseHexCodePoint(trimmed)
        val codePointMatch = hexCodePoint ?: directCodePoint
        if (codePointMatch != null) {
            val direct = unicodeStorage.findCharByCodePoint(codePointMatch, hasGlyph)
            if (direct != null) {
                results.add(direct)
            }
        }

        if (hexCodePoint == null && trimmed.isNotEmpty()) {
            val tokens = whitespaceRegex.split(trimmed)
            val nameResults = if (tokens.size == 1) {
                unicodeStorage.findCharsByName(tokens[0], count, hasGlyph)
            } else {
                unicodeStorage.findCharsByNameMultiWord(tokens, count, hasGlyph)
            }

            for (r in nameResults) {
                if (codePointMatch == null || r.codePoint.value != codePointMatch) {
                    results.add(r)
                }
            }
        }

        searchResult = results.toTypedArray()
    }

    private fun tryParseHexCodePoint(input: String): Int? {
        val stripped = when {
            input.startsWith("U+", ignoreCase = true) -> input.substring(2)
            input.startsWith("0x", ignoreCase = true) -> input.substring(2)
            input.startsWith("\\u", ignoreCase = true) -> input.substring(2)
            else -> return null
        }
        if (stripped.isEmpty() || stripped.length > 6) return null
        if (!stripped.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val value = stripped.toIntOrNull(16) ?: return null
        return if (value in 0..0x10FFFF) value else null
    }

    private fun tryParseCharLiteral(input: String): Int? {
        if (input.length > 2) return null
        if (input.codePointCount(0, input.length) != 1) return null
        return input.codePointAt(0)
    }

    companion object {
        private val whitespaceRegex = "\\s+".toRegex()
    }
}
