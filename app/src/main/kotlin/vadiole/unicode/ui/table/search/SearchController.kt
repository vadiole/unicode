package vadiole.unicode.ui.table.search

import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.SearchResult
import vadiole.unicode.data.UnicodeSearch
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.RecentRepository
import vadiole.unicode.search.SearchMatch
import vadiole.unicode.search.SearchResults
import vadiole.unicode.ui.extension.worker

class SearchController(
    private val unicodeStorage: UnicodeStorage,
    private val recentRepository: RecentRepository,
) {
    private val search = unicodeStorage.search
    private var results = SearchResults.EMPTY
    var recentResult: Array<SearchResult> = emptyArray()
    val queryTokens: Array<String>
        get() = results.tokens
    val resultCount: Int
        get() = results.codePoints.size
    val meaningsStart: Int
        get() = results.meaningsStart
    private var isLoadingMore = false

    suspend fun loadRecents() {
        val codePoints = recentRepository.getRecents()
        recentResult = worker {
            val data = unicodeStorage.data()
            val found = ArrayList<SearchResult>(codePoints.size)
            for (codePoint in codePoints) {
                if (search.isShown(codePoint.value)) found.add(SearchResult(codePoint, data.name(codePoint.value).orEmpty()))
            }
            found.toTypedArray()
        }
    }

    fun search(query: String, onResults: () -> Unit) {
        search.search(query, UnicodeSearch.PAGE_SIZE) {
            isLoadingMore = false
            results = it
            onResults()
        }
    }

    fun loadMore(position: Int, onResults: () -> Unit) {
        if (isLoadingMore || results.codePoints.size == results.total || position < results.codePoints.size - LOAD_MORE_DISTANCE) return
        isLoadingMore = true
        search.search(results.query, 0) {
            results = it
            onResults()
        }
    }

    fun cancel() {
        search.cancel()
        isLoadingMore = false
        results = SearchResults.EMPTY
    }

    fun result(position: Int): SearchResult {
        val codePoint = results.codePoints[position]
        val name = unicodeStorage.data().name(codePoint).orEmpty()
        return if (position < results.codeCount) {
            SearchResult(CodePoint(codePoint), name, results.codeLength, codePoint == results.codeExact)
        } else {
            SearchResult(CodePoint(codePoint), name)
        }
    }

    fun match(position: Int): SearchMatch? = search.match(results, position)

    private companion object {
        const val LOAD_MORE_DISTANCE = 16
    }
}
