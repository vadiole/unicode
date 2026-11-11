package vadiole.unicode.data

import android.content.Context
import android.os.Process
import android.util.SparseArray
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import vadiole.unicode.BuildConfig

class UnicodeStorage(private val context: Context) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val backgroundDispatcher = ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "revalidate")
    }.asCoroutineDispatcher()
    private val lock = Any()
    @Volatile
    private var loadedData: UnicodeData? = null
    private var loadedMeanings: TaggedTexts? = null
    private var blockIcons: SparseArray<BlockIconSource>? = null
    val cacheDir: File get() = File(context.filesDir, cacheDirName)
    val buildKey: String
        get() = if (BuildConfig.DEBUG) context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString() else BuildConfig.VERSION_CODE.toString()
    val search = UnicodeSearch(this)

    fun data(): UnicodeData {
        loadedData?.let { return it }
        synchronized(lock) {
            loadedData?.let { return it }
            File(context.filesDir, legacyDatabaseDir).deleteRecursively()
            return context.assets.open(dataName).buffered(1 shl 16).use { stream ->
                val reader = UnicodeDataReader(stream)
                val data = reader.data()
                loadedData = data
                if (!search.hasIndex(data)) loadedMeanings = reader.meanings()
                data
            }
        }
    }

    fun meanings(): TaggedTexts = synchronized(lock) {
        loadedMeanings ?: context.assets.open(dataName).buffered(1 shl 16).use { UnicodeDataReader(it).meanings() }.also { loadedMeanings = it }
    }

    fun releaseMeanings() = synchronized(lock) {
        loadedMeanings = null
    }

    suspend fun getTotalCharacters(): Int = withContext(dispatcher) { data().size }

    suspend fun getCodePoints(count: Int, background: Boolean, acceptGlyph: (String) -> Boolean): CodePointArray = withContext(if (background) backgroundDispatcher else dispatcher) {
        val data = data()
        val limit = if (count > 0) minOf(count, data.size) else data.size
        val all = data.codePoints(limit)
        val result = IntArray(limit)
        var size = 0
        var index = 0
        var range = 0
        while (index < limit) {
            val codePoint = all[index]
            while (range < data.rangeCount && data.rangeLast[range] < codePoint) range++
            if (range < data.rangeCount && data.rangeFirst[range] <= codePoint) {
                val last = minOf(index + data.rangeLast[range] - codePoint, data.ageRunEnd(index) - 1, limit - 1)
                size = acceptRange(all, index, last, result, size, acceptGlyph)
                index = last + 1
            } else {
                if (data.kindAt(index) == UnicodeData.KIND_ABBREVIATION || acceptGlyph(CodePoint(codePoint).char)) result[size++] = codePoint
                index++
            }
        }
        return@withContext CodePointArray(result.copyOf(size))
    }

    private fun acceptRange(all: IntArray, first: Int, last: Int, out: IntArray, start: Int, acceptGlyph: (String) -> Boolean): Int {
        var accepted = countAccepted(all, first, last, GLYPH_SAMPLES, acceptGlyph)
        var samples = minOf(GLYPH_SAMPLES, last - first + 1)
        if (accepted == 0 || accepted == samples) {
            accepted = countAccepted(all, first, last, GLYPH_UNANIMOUS_SAMPLES, acceptGlyph)
            samples = minOf(GLYPH_UNANIMOUS_SAMPLES, last - first + 1)
        }
        var size = start
        when (accepted) {
            samples -> for (index in first..last) out[size++] = all[index]
            0 -> Unit
            else -> for (index in first..last) if (acceptGlyph(CodePoint(all[index]).char)) out[size++] = all[index]
        }
        return size
    }

    private fun countAccepted(all: IntArray, first: Int, last: Int, count: Int, acceptGlyph: (String) -> Boolean): Int {
        val length = last - first + 1
        val samples = minOf(count, length)
        var accepted = 0
        for (i in 0 until samples) {
            val index = if (samples == 1) first else first + ((length - 1).toLong() * i / (samples - 1)).toInt()
            if (acceptGlyph(CodePoint(all[index]).char)) accepted++
        }
        return accepted
    }

    suspend fun readCache(name: String, key: String): IntArray? = withContext(dispatcher) {
        val file = File(cacheDir, name)
        if (!file.exists()) return@withContext null
        val data = data()
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readUTF() != "${data.checksum};$key") return@withContext null
                val size = input.readInt()
                if (size !in 0..data.size) return@withContext null
                val bytes = ByteArray(size * 4)
                input.readFully(bytes)
                val values = IntArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).asIntBuffer().get(values)
                values
            }
        } catch (e: IOException) {
            null
        }
    }

    suspend fun writeCache(name: String, key: String, values: IntArray) = withContext(dispatcher) {
        val dir = cacheDir.also { it.mkdirs() }
        val temp = File(dir, "$name.tmp")
        try {
            DataOutputStream(temp.outputStream().buffered()).use { output ->
                output.writeUTF("${data().checksum};$key")
                output.writeInt(values.size)
                val bytes = ByteBuffer.allocate(values.size * 4)
                bytes.asIntBuffer().put(values)
                output.write(bytes.array())
            }
            temp.renameTo(File(dir, name))
        } catch (e: IOException) {
            temp.delete()
        }
    }

    suspend fun getAbbreviations(): Map<CodePoint, String> = withContext(dispatcher) {
        val data = data()
        val result = HashMap<CodePoint, String>()
        var index = 0
        while (index < data.size) {
            val end = data.kindRunEnd(index)
            if (data.kindAt(index) == UnicodeData.KIND_ABBREVIATION) {
                for (i in index until end) {
                    val codePoint = data.codePointAt(i)
                    result[CodePoint(codePoint)] = abbreviation(data, codePoint) ?: deriveAbbreviation(data.name(codePoint).orEmpty())
                }
            }
            index = end
        }
        return@withContext result
    }

    private fun abbreviation(data: UnicodeData, codePoint: Int): String? {
        val names = data.secondNames
        var index = names.first(codePoint)
        while (index < names.size && names.codePoints[index] == codePoint) {
            if (names.tags[index].toInt() == UnicodeData.SECOND_ABBREVIATION) return names.texts[index]
            index++
        }
        return null
    }

    private fun deriveAbbreviation(name: String): String {
        val clean = name.substringBefore(" (")
        return buildString {
            var prev = ' '
            for (ch in clean) {
                when {
                    ch.isDigit() -> append(ch)
                    ch.isLetter() && !prev.isLetter() -> append(ch)
                }
                prev = ch
            }
        }.ifEmpty { "?" }
    }

    suspend fun getBlocks(): Array<Block> = withContext(dispatcher) {
        val data = data()
        Array(data.blockStart.size) { Block(it, data.blockStart[it], data.blockEnd[it], data.blockNames[it]) }
    }

    suspend fun getBlockIcons(): SparseArray<BlockIconSource> = withContext(dispatcher) {
        blockIcons?.let { return@withContext it }
        val result = SparseArray<BlockIconSource>(400)
        context.assets.open(blockIconsName).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val tokens = line.split(' ')
                if (tokens.size < 3) return@forEach
                val type = when (tokens[1]) {
                    "s" -> BlockList.TYPE_SCRIPT
                    "k" -> BlockList.TYPE_MARKS
                    "n" -> BlockList.TYPE_NOTHING
                    else -> BlockList.TYPE_SYMBOL
                }
                val codePoints = IntArray(tokens.size - 3)
                val flags = ByteArray(codePoints.size)
                for (index in codePoints.indices) {
                    val token = tokens[index + 3]
                    val mark = token.endsWith('m')
                    codePoints[index] = (if (mark) token.dropLast(1) else token).toInt(16)
                    flags[index] = if (mark) BlockList.FLAG_MARK.toByte() else 0
                }
                result.append(tokens[0].toInt(16), BlockIconSource(type, tokens[2].takeIf { it != "-" }, codePoints, flags))
            }
        }
        blockIcons = result
        return@withContext result
    }

    suspend fun getCharObj(codePoint: CodePoint): CharObj? = withContext(dispatcher) {
        val data = data()
        val name = data.name(codePoint.value) ?: return@withContext null
        CharObj(
            id = data.indexOf(codePoint.value),
            codePointRaw = codePoint.value,
            name = name,
            version = data.versionOf(codePoint.value),
            blockName = data.blockName(codePoint.value).orEmpty(),
            subhead = data.subhead(codePoint.value),
        )
    }

    companion object {
        private const val dataName = "u18.bin"
        private const val legacyDatabaseDir = "sql"
        private const val cacheDirName = "cache"
        private const val blockIconsName = "block_icons.txt"
        private const val GLYPH_SAMPLES = 16
        private const val GLYPH_UNANIMOUS_SAMPLES = 256
    }
}
