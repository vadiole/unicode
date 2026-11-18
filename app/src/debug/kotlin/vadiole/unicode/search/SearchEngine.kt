package vadiole.unicode.search

fun interface SearchEngine {
    fun search(query: String, limit: Int): SearchHits
}

class SearchHits(val codePoints: IntArray, val total: Int)
