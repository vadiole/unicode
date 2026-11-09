package vadiole.unicode.ui.table

import android.graphics.Paint
import android.os.Build
import android.util.Log
import vadiole.unicode.data.Block
import vadiole.unicode.data.BlockList
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.data.UnicodeStorage
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.extension.binarySearch
import vadiole.unicode.ui.extension.filterMaybe
import vadiole.unicode.ui.extension.worker

class TableController(
    private val unicodeStorage: UnicodeStorage,
    private val userConfig: UserConfig,
) {
    var totalChars = 0
    var tableChars: CodePointArray = CodePointArray(0)
    var abbreviations: Map<CodePoint, String> = emptyMap()
    @Volatile
    var blockList: BlockList = BlockList.EMPTY
        private set
    val blocks: Array<Block>
        get() = blockList.blocks
    private val blocksLock = Any()
    private var blocksGeneration = 0
    private val glyphPaint = Paint()
    private var lastBlock: Block? = null
    private var lastBlockIndex = -1

    suspend fun loadAbbreviations() = worker {
        abbreviations = unicodeStorage.getAbbreviations()
    }

    suspend fun loadCachedChars(): Boolean {
        val cached = unicodeStorage.readCache(CACHE_NAME, cacheKey(userConfig.showUnsupportedChars)) ?: return false
        tableChars = CodePointArray(cached)
        totalChars = cached.size
        return true
    }

    suspend fun loadChars(fast: Boolean): Boolean {
        val count = if (fast) 256 else -1
        val showAll = userConfig.showUnsupportedChars
        val codePoints = unicodeStorage.getCodePoints(count) { showAll || glyphPaint.hasGlyph(it) }
        val isChanged = !codePoints.storage.contentEquals(tableChars.storage)
        if (!fast && isChanged) unicodeStorage.writeCache(CACHE_NAME, cacheKey(showAll), codePoints.storage)
        tableChars = codePoints
        totalChars = if (fast) UnicodeStorage.totalCharacters else codePoints.size
        return isChanged
    }

    private fun cacheKey(showAll: Boolean) = "$showAll;${Build.FINGERPRINT}"

    suspend fun loadBlocks() = worker {
        val generation = synchronized(blocksLock) { ++blocksGeneration }
        val allBlocks = unicodeStorage.getBlocks()
        val filteredBlocks = allBlocks.filterMaybe { block ->
            getPosition(block) != 0 || block.start == 0
        }.toTypedArray()
        val candidates = unicodeStorage.getBlockIcons()
        val icons = IntArray(filteredBlocks.size * BlockList.ICON_SIZE)
        val showAll = userConfig.showUnsupportedChars
        var key = 0
        filteredBlocks.forEachIndexed { index, block ->
            while (key < candidates.size() && candidates.keyAt(key) < block.start) key++
            val curated = if (key < candidates.size() && candidates.keyAt(key) <= block.end) candidates.valueAt(key) else null
            fillBlockIcons(block, curated, icons, index * BlockList.ICON_SIZE, showAll)
        }
        synchronized(blocksLock) {
            if (generation != blocksGeneration) return@worker
            lastBlock = null
            lastBlockIndex = -1
            blockList = BlockList(filteredBlocks, icons)
        }
    }

    private fun fillBlockIcons(block: Block, curated: IntArray?, out: IntArray, offset: Int, showAll: Boolean) {
        var size = 0
        if (curated != null) {
            for (codePoint in curated) {
                if (size == BlockList.ICON_SIZE) return
                if (isIconGlyph(codePoint) && (showAll || hasIconGlyph(codePoint))) {
                    out[offset + size++] = codePoint
                }
            }
        }
        val chars = tableChars.storage
        var index = chars.binarySearch(block.start).let { if (it < 0) -it - 1 else it }
        while (size < BlockList.ICON_SIZE && index < chars.size && chars[index] <= block.end) {
            val codePoint = chars[index++]
            if (isIconGlyph(codePoint) && !out.contains(codePoint, offset, offset + size)) {
                out[offset + size++] = codePoint
            }
        }
    }

    private fun IntArray.contains(value: Int, from: Int, to: Int): Boolean {
        for (i in from until to) if (this[i] == value) return true
        return false
    }

    private fun hasIconGlyph(codePoint: Int): Boolean {
        val char = CodePoint(codePoint).char
        return glyphPaint.hasGlyph(if (BlockList.isMark(codePoint)) BlockList.MARK_BASE + char else char)
    }

    private fun isIconGlyph(codePoint: Int) = when (Character.getType(codePoint).toByte()) {
        Character.UNASSIGNED, Character.CONTROL, Character.FORMAT, Character.SPACE_SEPARATOR,
        Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.PRIVATE_USE, Character.SURROGATE -> false

        else -> true
    }

    fun getBlock(position: Int): Block? {
        if (position < 0) return null
        val blocks = blocks
        if (blocks.isEmpty()) return null
        if (position >= tableChars.size) return null
        val codePoint = tableChars[position]
        val last = lastBlock?.takeIf { lastBlockIndex < blocks.size && blocks[lastBlockIndex] === it }
        if (last == null) {
            val index = blocks.binarySearch { block ->
                block.contains(codePoint)
            }
            if (index < 0 || index >= blocks.size) {
                Log.e("TableController", "Code point $codePoint with index $index was not found in blocks (size: ${blocks.size})")
                return null
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
                if (index < 0 || index >= blocks.size) return null
                val block = blocks[index]
                lastBlockIndex = index
                lastBlock = block
                return block
            }

            codePoint.value > last.end -> {
                val index = blocks.binarySearch(fromIndex = lastBlockIndex) { block ->
                    block.contains(codePoint)
                }
                if (index < 0 || index >= blocks.size) return null
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

    private companion object {
        const val CACHE_NAME = "table"
    }
}
