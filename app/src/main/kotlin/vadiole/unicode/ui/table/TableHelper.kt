package vadiole.unicode.ui.table

import android.graphics.Paint
import vadiole.unicode.data.Block
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.utils.extension.worker

class TableHelper(private val unicodeStorage: UnicodeStorage, private val userConfig: UserConfig) {
    var totalChars = UnicodeStorage.totalCharacters
    var tableChars: List<CodePoint> = emptyList()
    var blocks: List<Block> = emptyList()
    private val glyphPaint = Paint()
    private var lastBlock: Block? = null
    private var lastBlockIndex = -1

    suspend fun loadChars(fast: Boolean) = worker {
        val count = if (fast) 256 else -1
        var codePoints = unicodeStorage.getCodePoints(count)
        if (!userConfig.showUnsupportedChars) {
            codePoints = codePoints.filter { glyphPaint.hasGlyph(it.char) }
        }
        tableChars = codePoints
        if (!fast) {
            totalChars = codePoints.size
        }
    }

    suspend fun loadBlocks() = worker {
        blocks = unicodeStorage.getBlocks()
    }

    fun getBlock(position: Int): Block? {
        if (position == 0) return null
        if (blocks.isEmpty()) return null
        val codePoint = tableChars.getOrNull(position) ?: return null
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

    fun getChars(position: Int, spanCount: Int): Array<CodePoint> {
        val codePoints = Array(spanCount) { index ->
            val globalIndex = position * spanCount + index
            tableChars.getOrNull(globalIndex) ?: CodePoint(0)
        }
        return codePoints
    }
}