package vadiole.unicode.ui.table

import android.graphics.Paint
import android.os.Build
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import vadiole.unicode.data.Block
import vadiole.unicode.data.BlockIconSource
import vadiole.unicode.data.BlockList
import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.CodePointArray
import vadiole.unicode.data.UnicodeData
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
    @Volatile
    var scrollScale: ScrollScale? = null
        private set
    private val blocksLock = Any()
    private var blocksGeneration = 0
    private val glyphPaint = Paint()
    private var lastBlock: Block? = null
    private var lastBlockIndex = -1

    suspend fun loadAbbreviations() = worker {
        abbreviations = unicodeStorage.getAbbreviations()
    }

    suspend fun awaitSearch() = suspendCancellableCoroutine { continuation ->
        unicodeStorage.search.whenReady { if (continuation.isActive) continuation.resume(Unit) }
    }

    fun needsRevalidation(): Boolean {
        if (isRevalidated) return false
        isRevalidated = true
        return true
    }

    suspend fun loadCachedChars(): Boolean {
        val cached = unicodeStorage.readCache(CACHE_NAME, cacheKey(userConfig.showUnsupportedChars)) ?: return false
        tableChars = CodePointArray(cached)
        totalChars = cached.size
        updateShown()
        return true
    }

    suspend fun loadChars(fast: Boolean, background: Boolean = false): Boolean {
        val count = if (fast) 256 else -1
        val showAll = userConfig.showUnsupportedChars
        val paint = if (background) Paint() else glyphPaint
        val codePoints = unicodeStorage.getCodePoints(count, background) { showAll || paint.hasGlyph(it) }
        if (showAll != userConfig.showUnsupportedChars) return false
        val isChanged = !codePoints.storage.contentEquals(tableChars.storage)
        if (!fast && isChanged) unicodeStorage.writeCache(CACHE_NAME, cacheKey(showAll), codePoints.storage)
        tableChars = codePoints
        totalChars = if (fast) unicodeStorage.getTotalCharacters() else codePoints.size
        if (!fast) updateShown()
        return isChanged
    }

    private fun updateShown() {
        unicodeStorage.search.setShown(if (userConfig.showUnsupportedChars) null else tableChars.storage)
    }

    private fun cacheKey(showAll: Boolean) = "$showAll;${Build.FINGERPRINT};${unicodeStorage.buildKey}"

    suspend fun loadBlocks() = worker {
        val generation = synchronized(blocksLock) { ++blocksGeneration }
        val allBlocks = unicodeStorage.getBlocks()
        val filteredBlocks = allBlocks.filterMaybe { block ->
            getPosition(block) != 0 || block.start == 0
        }.toTypedArray()
        val sources = unicodeStorage.getBlockIcons()
        val icons = IntArray(filteredBlocks.size * BlockList.ICON_SLOTS)
        val iconFlags = ByteArray(icons.size)
        val types = ByteArray(filteredBlocks.size)
        val labels = arrayOfNulls<String>(filteredBlocks.size)
        val showAll = userConfig.showUnsupportedChars
        val iconPaint = Paint()
        val data = unicodeStorage.data()
        filteredBlocks.forEachIndexed { index, block ->
            val source = sources.get(block.start)
            types[index] = (source?.type ?: BlockList.TYPE_SYMBOL).toByte()
            labels[index] = source?.label
            fillBlockIcons(block, source, data, icons, iconFlags, index * BlockList.ICON_SLOTS, showAll, iconPaint)
        }
        val scale = ScrollScale.create(tableChars.storage, filteredBlocks, data)
        synchronized(blocksLock) {
            if (generation != blocksGeneration) return@worker
            lastBlock = null
            lastBlockIndex = -1
            blockList = BlockList(filteredBlocks, icons, iconFlags, types, labels)
            scrollScale = scale
        }
    }

    private fun fillBlockIcons(block: Block, source: BlockIconSource?, data: UnicodeData, out: IntArray, outFlags: ByteArray, offset: Int, showAll: Boolean, paint: Paint) {
        var size = 0
        if (source != null) {
            val codePoints = source.codePoints
            val rendered = BooleanArray(codePoints.size)
            for (index in codePoints.indices) {
                val flags = source.flags[index].toInt()
                rendered[index] = hasIconGlyph(paint, codePoints[index], flags and BlockList.FLAG_MARK != 0)
                if (rendered[index] && size < BlockList.ICON_SLOTS) {
                    out[offset + size] = codePoints[index]
                    outFlags[offset + size++] = flags.toByte()
                }
            }
            if (showAll) {
                for (index in codePoints.indices) {
                    if (size == BlockList.ICON_SLOTS) break
                    if (rendered[index]) continue
                    out[offset + size] = codePoints[index]
                    outFlags[offset + size++] = source.flags[index]
                }
            }
            if (source.type == BlockList.TYPE_NOTHING) return
        }
        val markFlag = if (source?.type == BlockList.TYPE_MARKS) BlockList.FLAG_MARK.toByte() else 0
        val chars = tableChars.storage
        var index = chars.binarySearch(block.start).let { if (it < 0) -it - 1 else it }
        while (size < BlockList.ICON_SLOTS && index < chars.size && chars[index] <= block.end) {
            val codePoint = chars[index++]
            val dataIndex = data.indexOf(codePoint)
            if (dataIndex < 0 || data.kindAt(dataIndex) == UnicodeData.KIND_ABBREVIATION) continue
            if (!out.contains(codePoint, offset, offset + size)) {
                out[offset + size] = codePoint
                outFlags[offset + size++] = markFlag
            }
        }
    }

    private fun IntArray.contains(value: Int, from: Int, to: Int): Boolean {
        for (i in from until to) if (this[i] == value) return true
        return false
    }

    private fun hasIconGlyph(paint: Paint, codePoint: Int, mark: Boolean): Boolean {
        val char = CodePoint(codePoint).char
        return paint.hasGlyph(char) || (mark && paint.hasGlyph(BlockList.MARK_BASE + char))
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
        var isRevalidated = false
    }
}
