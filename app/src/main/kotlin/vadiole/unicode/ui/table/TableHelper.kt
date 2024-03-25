package vadiole.unicode.ui.table

import android.graphics.Paint
import vadiole.unicode.data.Block
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.data.filterMaybe
import vadiole.unicode.utils.extension.binarySearch
import vadiole.unicode.utils.extension.filterMaybe
import vadiole.unicode.utils.extension.worker

class TableHelper(private val unicodeStorage: UnicodeStorage, private val userConfig: UserConfig) {
    var totalChars = UnicodeStorage.totalCharacters
    var tableChars: CodePointArray = CodePointArray(0)
    var blocks: Array<Block> = emptyArray()
    private val glyphPaint = Paint()
    private var lastBlock: Block? = null
    private var lastBlockIndex = -1

    suspend fun loadChars(fast: Boolean) = worker {
        val count = if (fast) 256 else -1
        var codePoints = unicodeStorage.getCodePoints(count)
        if (!userConfig.showUnsupportedChars) {
            codePoints = codePoints.filterMaybe { glyphPaint.hasGlyph(it.char) }
        }
        tableChars = codePoints
        if (!fast) {
            totalChars = codePoints.size
        }
    }

    suspend fun loadBlocks() = worker {
        blocks = unicodeStorage.getBlocks()
        blocks = blocks.filterMaybe { block ->
            getPosition(block) != 0 || block.start == 0
        }.toTypedArray()
    }

    fun getBlock(position: Int): Block? {
        if (position <= 0) return null
        if (blocks.isEmpty()) return null
        if (position >= tableChars.size) return null
        val codePoint = tableChars[position]
        val last = lastBlock
        if (last == null) {
            val index = blocks.binarySearch { block ->
                block.contains(codePoint)
            }
            val block = blocks[index]
            lastBlockIndex = index
            lastBlock = block
            return block
        }
        when {
            codePoint.value < last.start -> {
                val index = blocks.binarySearch(toIndex = lastBlockIndex) { block ->
                    block.contains(codePoint)
                }
                val block = blocks[index]
                lastBlockIndex = index
                lastBlock = block
                return block
            }

            codePoint.value > last.end -> {
                val index = blocks.binarySearch(fromIndex = lastBlockIndex) { block ->
                    block.contains(codePoint)
                }
                val block = blocks[index]
                lastBlockIndex = index
                lastBlock = block
                return block
            }

            else -> {
                return last
            }
        }
    }

    fun getPosition(block: Block): Int {
        if (tableChars.isEmpty()) return 0
        if (blocks.isEmpty()) return 0
        if (block.start == 0) return 0

        var low = 0
        var high = tableChars.size - 1
        var position = 0

        while (low <= high) {
            val mid = (low + high).ushr(1) // safe from overflows
            val midVal = tableChars[mid]

            val cmp = block.contains(midVal)
            when {
                cmp > 0 -> {
                    low = mid + 1
                }

                cmp < 0 -> {
                    high = mid - 1
                }

                else -> {
                    position = mid
                    high = mid - 1
                }
            }
        }

        return position
    }

    fun getChars(position: Int, spanCount: Int): CodePointArray {
        val codePoints = CodePointArray(spanCount) { index ->
            val globalIndex = position * spanCount + index
            if (globalIndex >= tableChars.size) {
                CodePoint(0)
            } else {
                tableChars[globalIndex]
            }
        }
        return codePoints
    }
}
