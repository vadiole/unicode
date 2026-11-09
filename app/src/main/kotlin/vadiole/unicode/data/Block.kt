package vadiole.unicode.data

class Block(val id: Int, val start: Int, val end: Int, val name: String) {
    fun contains(codePoint: CodePoint) = when {
        codePoint.value < start -> 1
        codePoint.value > end -> -1
        else -> 0
    }
}

class BlockList(val blocks: Array<Block>, val icons: IntArray) {
    companion object {
        const val ICON_SIZE = 4
        const val MARK_BASE = '\u25CC'
        val EMPTY = BlockList(emptyArray(), IntArray(0))

        fun isMark(codePoint: Int) = when (Character.getType(codePoint).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }
}
