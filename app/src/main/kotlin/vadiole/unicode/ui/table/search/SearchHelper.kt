package vadiole.unicode.ui.table.search

import android.graphics.Paint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.utils.extension.filterMaybe

class SearchHelper(private val unicodeStorage: UnicodeStorage) {
    private val glyphPaint = Paint()
    var searchResult: Array<SearchResult> = emptyArray()
    suspend fun search(query: String) {
        searchResult = if (query.isBlank()) {
            emptyArray()
        } else {
            unicodeStorage.findCharsByName(query)
                .filterMaybe { glyphPaint.hasGlyph(it.codePoint.char) }
                .toTypedArray()
        }
    }
}
