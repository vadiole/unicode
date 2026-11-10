package vadiole.unicode.data

class Block(val id: Int, val start: Int, val end: Int, val name: String) {
    fun contains(codePoint: CodePoint) = when {
        codePoint.value < start -> 1
        codePoint.value > end -> -1
        else -> 0
    }
}

class BlockIconSource(val type: Int, val label: String?, val codePoints: IntArray, val flags: ByteArray)

class BlockList(
    val blocks: Array<Block>,
    val icons: IntArray,
    val iconFlags: ByteArray,
    val types: ByteArray,
    val labels: Array<String?>,
) {
    companion object {
        const val ICON_SLOTS = 4
        const val MARK_BASE = '◌'
        const val FLAG_MARK = 1
        const val TYPE_SCRIPT = 0
        const val TYPE_SYMBOL = 1
        const val TYPE_MARKS = 2
        const val TYPE_NOTHING = 3
        val EMPTY = BlockList(emptyArray(), IntArray(0), ByteArray(0), ByteArray(0), emptyArray())
    }
}
