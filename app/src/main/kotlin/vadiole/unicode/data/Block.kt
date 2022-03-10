package vadiole.unicode.data

class Block(val id: Int, val start: Int, val end: Int, val name: String) {
    fun contains(codePoint: CodePoint) = when {
        codePoint.value < start -> 1
        codePoint.value > end -> -1
        else -> 0
    }
}