package vadiole.unicode.ui.table.search

import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.UnicodeStorage

class SearchHelper(private val unicodeStorage: UnicodeStorage) {
    var searchResult: Array<SearchResult> = emptyArray()
    suspend fun search(query: String) {
        searchResult = if (query.isBlank()) {
            emptyArray()
        } else {
            unicodeStorage.findCharsByName(query)
        }
    }
}