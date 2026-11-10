package vadiole.unicode.data

import android.content.Context
import android.util.SparseArray
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class UnicodeStorage(private val context: Context) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val lock = Any()
    @Volatile
    private var loadedData: UnicodeData? = null
    private var loadedMeanings: TaggedTexts? = null
    private var blockIcons: SparseArray<IntArray>? = null
    val cacheDir: File get() = File(context.filesDir, cacheDirName)
    val search = UnicodeSearch(this)

    fun data(): UnicodeData {
        loadedData?.let { return it }
        synchronized(lock) {
            loadedData?.let { return it }
            File(context.filesDir, legacyDatabaseDir).deleteRecursively()
            val data = context.assets.open(dataName).buffered(1 shl 16).use { UnicodeData.read(it) }
            loadedData = data
            return data
        }
    }

    fun meanings(): TaggedTexts = synchronized(lock) {
        loadedMeanings ?: context.assets.open(dataName).buffered(1 shl 16).use { UnicodeData.readMeanings(it) }.also { loadedMeanings = it }
    }

    fun releaseMeanings() = synchronized(lock) {
        loadedMeanings = null
    }

    suspend fun getMeaning(codePoint: CodePoint): String? = withContext(dispatcher) {
        val meanings = meanings()
        val index = meanings.first(codePoint.value)
        if (index < meanings.size && meanings.codePoints[index] == codePoint.value) meanings.texts[index] else null
    }

    suspend fun getTotalCharacters(): Int = withContext(dispatcher) { data().size }

    suspend fun getCodePoints(count: Int, acceptGlyph: (String) -> Boolean): CodePointArray = withContext(dispatcher) {
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
        val length = last - first + 1
        val samples = minOf(GLYPH_SAMPLES, length)
        var accepted = 0
        for (i in 0 until samples) {
            val index = if (samples == 1) first else first + ((length - 1).toLong() * i / (samples - 1)).toInt()
            if (acceptGlyph(CodePoint(all[index]).char)) accepted++
        }
        var size = start
        when (accepted) {
            samples -> for (index in first..last) out[size++] = all[index]
            0 -> Unit
            else -> for (index in first..last) if (acceptGlyph(CodePoint(all[index]).char)) out[size++] = all[index]
        }
        return size
    }

    suspend fun readCache(name: String, key: String): IntArray? = withContext(dispatcher) {
        val file = File(cacheDir, name)
        if (!file.exists()) return@withContext null
        val data = data()
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readUTF() != "${data.version};$key") return@withContext null
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
                output.writeUTF("${data().version};$key")
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
                    result[CodePoint(codePoint)] = ccOverrides[codePoint] ?: deriveAbbreviation(data.name(codePoint).orEmpty())
                }
            }
            index = end
        }
        return@withContext result
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

    suspend fun getBlockIcons(): SparseArray<IntArray> = withContext(dispatcher) {
        blockIcons?.let { return@withContext it }
        val result = SparseArray<IntArray>(400)
        context.assets.open(blockIconsName).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val tokens = line.trim().split(whitespace)
                val start = tokens[0].toIntOrNull(16) ?: return@forEach
                val icons = IntArray(tokens.size - 1)
                var size = 0
                for (index in 1 until tokens.size) {
                    val token = tokens[index]
                    val codePoint = token.codePointAt(0)
                    if (token.length == Character.charCount(codePoint)) icons[size++] = codePoint
                }
                if (size > 0) result.append(start, icons.copyOf(size))
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
        )
    }

    companion object {
        private const val dataName = "u18.bin"
        private const val legacyDatabaseDir = "sql"
        private const val cacheDirName = "cache"
        private const val blockIconsName = "block_icons.txt"
        private const val GLYPH_SAMPLES = 16
        private val whitespace = Regex("\\s+")

        private val ccOverrides: Map<Int, String> = mapOf(
            0x00 to "NUL", 0x01 to "SOH", 0x02 to "STX", 0x03 to "ETX",
            0x04 to "EOT", 0x05 to "ENQ", 0x06 to "ACK", 0x07 to "BEL",
            0x08 to "BS", 0x09 to "HT", 0x0A to "LF", 0x0B to "VT",
            0x0C to "FF", 0x0D to "CR", 0x0E to "SO", 0x0F to "SI",
            0x10 to "DLE", 0x11 to "DC1", 0x12 to "DC2", 0x13 to "DC3",
            0x14 to "DC4", 0x15 to "NAK", 0x16 to "SYN", 0x17 to "ETB",
            0x18 to "CAN", 0x19 to "EM", 0x1A to "SUB", 0x1B to "ESC",
            0x1C to "FS", 0x1D to "GS", 0x1E to "RS", 0x1F to "US",
            0x7F to "DEL",
            0x80 to "PAD", 0x81 to "HOP", 0x82 to "BPH", 0x83 to "NBH",
            0x84 to "IND", 0x85 to "NEL", 0x86 to "SSA", 0x87 to "ESA",
            0x88 to "HTS", 0x89 to "HTJ", 0x8A to "VTS", 0x8B to "PLD",
            0x8C to "PLU", 0x8D to "RI", 0x8E to "SS2", 0x8F to "SS3",
            0x90 to "DCS", 0x91 to "PU1", 0x92 to "PU2", 0x93 to "STS",
            0x94 to "CCH", 0x95 to "MW", 0x96 to "SPA", 0x97 to "EPA",
            0x98 to "SOS", 0x99 to "SGC", 0x9A to "SCI", 0x9B to "CSI",
            0x9C to "ST", 0x9D to "OSC", 0x9E to "PM", 0x9F to "APC",
        )
    }
    }
