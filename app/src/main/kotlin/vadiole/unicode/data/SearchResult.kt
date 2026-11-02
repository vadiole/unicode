package vadiole.unicode.data

class SearchResult(
    val codePoint: CodePoint,
    val name: String,
    val codeMatchLength: Int = 0,
    val codeMatchAtEnd: Boolean = false,
)
