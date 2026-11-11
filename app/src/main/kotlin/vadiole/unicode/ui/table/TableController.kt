package vadiole.unicode.ui.table

import android.graphics.Paint
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
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
import vadiole.unicode.data.binarySearch
import vadiole.unicode.data.worker

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
    private val glyphPaint = Paint()
    private val cacheLock = Any()
    private var blocksGeneration = 0
    private var lastBlock: Block? = null
    private var lastBlockIndex = -1

    fun startSearch() = unicodeStorage.search.start()

    suspend fun awaitSearch() = suspendCancellableCoroutine { continuation ->
        unicodeStorage.search.whenReady { if (continuation.isActive) continuation.resume(Unit) }
    }

    fun needsRevalidation() = !isRevalidated

    fun loadCache(): Boolean {
        val showAll = userConfig.showUnsupportedChars
        val task = preloaded?.takeIf { preloadedShowAll == showAll }
        preloaded = null
        val latest = latest
        val cache = when {
            task != null -> try {
                task.get()
            } catch (e: ExecutionException) {
                null
            }
            latest?.showAll == showAll -> latest.cache
            else -> TableCache.read(cacheFile(unicodeStorage), cacheKey(unicodeStorage, showAll))
        } ?: return false
        Companion.latest = LatestCache(showAll, cache)
        tableChars = CodePointArray(cache.chars)
        totalChars = cache.chars.size
        abbreviations = cache.abbreviations
        synchronized(blocksLock) {
            blockList = cache.blockList
            scrollScale = cache.scrollScale
        }
        updateShown()
        return true
    }

    suspend fun loadPreview(untilCodePoint: Int) {
        val showAll = userConfig.showUnsupportedChars
        val (codePoints, abbreviations) = worker {
            val count = unicodeStorage.data().lowerIndex(untilCodePoint) + PREVIEW_SIZE
            unicodeStorage.getCodePoints(count, background = false) { showAll || glyphPaint.hasGlyph(it) } to unicodeStorage.getAbbreviations()
        }
        tableChars = codePoints
        totalChars = unicodeStorage.data().size
        this.abbreviations = abbreviations
    }

    suspend fun loadChars(background: Boolean = false): Boolean {
        val showAll = userConfig.showUnsupportedChars
        val paint = if (background) Paint() else glyphPaint
        val codePoints = unicodeStorage.getCodePoints(-1, background) { showAll || paint.hasGlyph(it) }
        if (showAll != userConfig.showUnsupportedChars) return false
        isRevalidated = true
        val isChanged = !codePoints.storage.contentEquals(tableChars.storage)
        tableChars = codePoints
        totalChars = codePoints.size
        updateShown()
        return isChanged
    }

    private fun updateShown() {
        unicodeStorage.search.setShown(if (userConfig.showUnsupportedChars) null else tableChars.storage)
    }

    suspend fun loadBlocks() = worker {
        val generation = synchronized(blocksLock) { ++blocksGeneration }
        val chars = tableChars
        val showAll = userConfig.showUnsupportedChars
        val allBlocks = unicodeStorage.getBlocks()
        val filteredBlocks = allBlocks.filter { block ->
            getPosition(block) != 0 || block.start == 0
        }.toTypedArray()
        val sources = unicodeStorage.getBlockIcons()
        val icons = IntArray(filteredBlocks.size * BlockList.ICON_SLOTS)
        val iconFlags = ByteArray(icons.size)
        val types = ByteArray(filteredBlocks.size)
        val labels = arrayOfNulls<String>(filteredBlocks.size)
        val iconPaint = Paint()
        val data = unicodeStorage.data()
        filteredBlocks.forEachIndexed { index, block ->
            val source = sources.get(block.start)
            types[index] = (source?.type ?: BlockList.TYPE_SYMBOL).toByte()
            labels[index] = source?.label
            fillBlockIcons(block, source, data, chars.storage, icons, iconFlags, index * BlockList.ICON_SLOTS, showAll, iconPaint)
        }
        val scale = ScrollScale.create(chars.storage, filteredBlocks, data)
        val list = BlockList(filteredBlocks, icons, iconFlags, types, labels)
        synchronized(blocksLock) {
            if (generation != blocksGeneration) return@worker
            lastBlock = null
            lastBlockIndex = -1
            blockList = list
            scrollScale = scale
        }
        val file = cacheFile(unicodeStorage)
        file.parentFile?.mkdirs()
        synchronized(cacheLock) {
            if (generation != blocksGeneration) return@worker
            val cache = TableCache(chars.storage, abbreviations, list, scale)
            cache.write(file, cacheKey(unicodeStorage, showAll))
            latest = LatestCache(showAll, cache)
        }
    }

    private fun fillBlockIcons(block: Block, source: BlockIconSource?, data: UnicodeData, chars: IntArray, out: IntArray, outFlags: ByteArray, offset: Int, showAll: Boolean, paint: Paint) {
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

    companion object {
        private const val CACHE_NAME = "table"
        private const val PREVIEW_SIZE = 256
        private const val CACHE_FORMAT = 2
        private var isRevalidated = false
        private var preloaded: FutureTask<TableCache?>? = null
        private var preloadedShowAll = false
        @Volatile
        private var latest: LatestCache? = null

        fun preload(storage: UnicodeStorage, showAll: Boolean) {
            val task = FutureTask { TableCache.read(cacheFile(storage), cacheKey(storage, showAll)) }
            preloaded = task
            preloadedShowAll = showAll
            Thread({
                task.run()
                if (task.get() == null) storage.data()
            }, "table-cache").start()
        }

        private class LatestCache(val showAll: Boolean, val cache: TableCache)

        private fun cacheFile(storage: UnicodeStorage) = File(storage.cacheDir, CACHE_NAME)

        private fun cacheKey(storage: UnicodeStorage, showAll: Boolean) = "$CACHE_FORMAT;$showAll;${Build.FINGERPRINT};${storage.buildKey}"
    }
}
