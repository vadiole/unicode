package vadiole.unicode.data

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class TextTable(val bytes: ByteArray) {
    val offsets: IntArray
    val size: Int get() = offsets.size - 1

    init {
        var lines = 0
        for (b in bytes) if (b == NEW_LINE) lines++
        offsets = IntArray(lines + 1)
        var line = 0
        for (i in bytes.indices) if (bytes[i] == NEW_LINE) offsets[++line] = i + 1
    }

    fun start(index: Int) = offsets[index]

    fun end(index: Int) = offsets[index + 1] - 1

    operator fun get(index: Int) = String(bytes, offsets[index], offsets[index + 1] - 1 - offsets[index], Charsets.UTF_8)

    private companion object {
        const val NEW_LINE = '\n'.code.toByte()
    }
}

class TaggedTexts(val codePoints: IntArray, val tags: ByteArray, val texts: TextTable) {
    val size: Int get() = codePoints.size

    fun first(codePoint: Int): Int {
        var low = 0
        var high = codePoints.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (codePoints[mid] < codePoint) low = mid + 1 else high = mid
        }
        return low
    }

    fun count(codePoint: Int, first: Int): Int {
        var end = first
        while (end < codePoints.size && codePoints[end] == codePoint) end++
        return end - first
    }

    companion object {
        fun read(meta: ByteArray, text: ByteArray): TaggedTexts {
            val reader = VarintReader(meta)
            val size = reader.next()
            val codePoints = IntArray(size)
            val tags = ByteArray(size)
            var last = 0
            for (i in 0 until size) {
                last += reader.next()
                codePoints[i] = last
                tags[i] = reader.byte()
            }
            val texts = TextTable(text)
            if (texts.size != size) throw IOException("Text count mismatch")
            return TaggedTexts(codePoints, tags, texts)
        }
    }
}

class SecondName(val kind: Int, val text: String)

class VarintReader(private val bytes: ByteArray) {
    private var position = 0

    fun next(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = bytes[position++].toInt()
            value = value or ((b and 0x7F) shl shift)
            if (b >= 0) return value
            shift += 7
        }
    }

    fun byte(): Byte = bytes[position++]
}

class UnicodeData(
    val checksum: Int,
    private val runStart: IntArray,
    private val runIndex: IntArray,
    private val kindEnd: IntArray,
    private val kindValue: ByteArray,
    private val ageEnd: IntArray,
    private val ageValue: ByteArray,
    private val versions: Array<String>,
    val blockStart: IntArray,
    val blockEnd: IntArray,
    val blockNames: TextTable,
    val subheadStart: IntArray,
    val subheadEnd: IntArray,
    val subheads: TextTable,
    val rangeFirst: IntArray,
    val rangeLast: IntArray,
    private val rangeType: ByteArray,
    val rangePrefixes: Array<String>,
    val names: TextTable,
    val secondNames: TaggedTexts,
    val keywords: TaggedTexts,
) {
    val size: Int = runIndex[runIndex.size - 1]
    val rangeCount: Int get() = rangeFirst.size
    private val rangeSkip = IntArray(rangeFirst.size + 1).also { skip ->
        for (i in rangeFirst.indices) skip[i + 1] = skip[i] + rangeLast[i] - rangeFirst[i] + 1
    }

    fun indexOf(codePoint: Int): Int {
        val run = findStart(runStart, runStart.size, codePoint)
        if (run < 0) return -1
        val index = runIndex[run] + codePoint - runStart[run]
        return if (index < runIndex[run + 1]) index else -1
    }

    fun lowerIndex(codePoint: Int): Int {
        val run = findStart(runStart, runStart.size, codePoint)
        if (run < 0) return 0
        return minOf(runIndex[run] + codePoint - runStart[run], runIndex[run + 1])
    }

    fun codePointAt(index: Int): Int {
        val run = findStart(runIndex, runStart.size, index)
        return runStart[run] + index - runIndex[run]
    }

    fun codePoints(count: Int): IntArray {
        val result = IntArray(count)
        var run = 0
        for (index in 0 until count) {
            if (index == runIndex[run + 1]) run++
            result[index] = runStart[run] + index - runIndex[run]
        }
        return result
    }

    fun kindAt(index: Int): Int = kindValue[findEnd(kindEnd, index)].toInt()

    fun kindRunEnd(index: Int): Int = kindEnd[findEnd(kindEnd, index)]

    fun ageRunEnd(index: Int): Int = ageEnd[findEnd(ageEnd, index)]

    fun versionOf(codePoint: Int): String {
        val index = indexOf(codePoint)
        return if (index < 0) "" else versions[ageValue[findEnd(ageEnd, index)].toInt()]
    }

    fun rangeOf(codePoint: Int): Int {
        for (i in rangeFirst.indices) {
            if (codePoint < rangeFirst[i]) return -1
            if (codePoint <= rangeLast[i]) return i
        }
        return -1
    }

    fun isHangulRange(range: Int) = rangeType[range].toInt() == RANGE_HANGUL

    fun nameIndex(codePoint: Int): Int {
        val index = indexOf(codePoint)
        if (index < 0) return -1
        var range = 0
        while (range < rangeFirst.size && rangeLast[range] < codePoint) range++
        if (range < rangeFirst.size && rangeFirst[range] <= codePoint) return -1
        return index - rangeSkip[range]
    }

    fun name(codePoint: Int): String? {
        val index = nameIndex(codePoint)
        if (index >= 0) return names[index]
        val range = rangeOf(codePoint)
        if (range < 0) return null
        return if (isHangulRange(range)) {
            rangePrefixes[range] + ' ' + HangulNames.name(codePoint)
        } else {
            rangePrefixes[range] + '-' + Integer.toHexString(codePoint).uppercase(Locale.ROOT)
        }
    }

    fun blockName(codePoint: Int): String? {
        val index = findSpan(blockStart, blockEnd, codePoint)
        return if (index < 0) null else blockNames[index]
    }

    fun subhead(codePoint: Int): String? {
        val index = findSpan(subheadStart, subheadEnd, codePoint)
        return if (index < 0) null else subheads[index]
    }

    fun secondNames(codePoint: Int): Array<SecondName> {
        val first = secondNames.first(codePoint)
        return Array(secondNames.count(codePoint, first)) { SecondName(secondNames.tags[first + it].toInt(), secondNames.texts[first + it]) }
    }

    fun displayKeywords(codePoint: Int): Array<String> {
        val first = keywords.first(codePoint)
        var count = 0
        while (first + count < keywords.size && keywords.codePoints[first + count] == codePoint && keywords.tags[first + count].toInt() == KEYWORD_DISPLAY) count++
        return Array(count) { keywords.texts[first + it] }
    }

    private fun findStart(starts: IntArray, count: Int, value: Int): Int {
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= value) low = mid + 1 else high = mid - 1
        }
        return high
    }

    private fun findEnd(ends: IntArray, index: Int): Int {
        var low = 0
        var high = ends.size - 1
        while (low < high) {
            val mid = (low + high) ushr 1
            if (ends[mid] <= index) low = mid + 1 else high = mid
        }
        return low
    }

    private fun findSpan(starts: IntArray, ends: IntArray, codePoint: Int): Int {
        val span = findStart(starts, starts.size, codePoint)
        return if (span >= 0 && codePoint <= ends[span]) span else -1
    }

    companion object {
        const val MAGIC = 0x55434431
        const val VERSION = 2
        const val KIND_NORMAL = 1
        const val KIND_ABBREVIATION = 2
        const val RANGE_HEX = 0
        const val RANGE_HANGUL = 1
        const val SECOND_UNICODE_1 = 0
        const val SECOND_CORRECTION = 1
        const val SECOND_CONTROL = 2
        const val SECOND_ALTERNATE = 3
        const val SECOND_FIGMENT = 4
        const val SECOND_ABBREVIATION = 5
        const val SECOND_ALIAS = 6
        const val SECOND_SHORT_NAME = 7
        const val KEYWORD_DISPLAY = 1
        const val HAN_SCHOOL = 0
        const val HAN_CORE = 1
        const val HAN_OTHER = 2
        private const val SECTIONS = 17
        private const val MEANING_SECTIONS = 2

        fun read(header: IntArray, input: DataInputStream): UnicodeData {
            val sections = Array(SECTIONS - MEANING_SECTIONS) { ByteArray(header[it]).also(input::readFully) }

            var reader = VarintReader(sections[0])
            val runs = reader.next()
            val runStart = IntArray(runs)
            val runIndex = IntArray(runs + 1)
            var next = 0
            for (i in 0 until runs) {
                runStart[i] = next + reader.next()
                val length = reader.next() + 1
                runIndex[i + 1] = runIndex[i] + length
                next = runStart[i] + length
            }
            val kindEnd: IntArray
            val kindValue: ByteArray
            readRuns(sections[1]).let { (ends, values) ->
                kindEnd = ends
                kindValue = values
            }
            val ageEnd: IntArray
            val ageValue: ByteArray
            readRuns(sections[2]).let { (ends, values) ->
                ageEnd = ends
                ageValue = values
            }
            val versions = TextTable(sections[3]).let { table -> Array(table.size) { table[it] } }
            val (blockStart, blockEnd) = readSpans(sections[4])
            val (subheadStart, subheadEnd) = readSpans(sections[6])
            reader = VarintReader(sections[8])
            val ranges = reader.next()
            val rangeFirst = IntArray(ranges)
            val rangeLast = IntArray(ranges)
            val rangeType = ByteArray(ranges)
            for (i in 0 until ranges) {
                rangeFirst[i] = reader.next()
                rangeLast[i] = rangeFirst[i] + reader.next()
                rangeType[i] = reader.byte()
            }
            val rangePrefixes = TextTable(sections[9]).let { table -> Array(table.size) { table[it] } }
            return UnicodeData(
                header[SECTIONS], runStart, runIndex, kindEnd, kindValue, ageEnd, ageValue, versions,
                blockStart, blockEnd, TextTable(sections[5]), subheadStart, subheadEnd, TextTable(sections[7]),
                rangeFirst, rangeLast, rangeType, rangePrefixes, TextTable(sections[10]),
                TaggedTexts.read(sections[11], sections[12]), TaggedTexts.read(sections[13], sections[14]),
            )
        }

        fun readMeanings(header: IntArray, input: DataInputStream): TaggedTexts {
            val meta = ByteArray(header[SECTIONS - 2]).also(input::readFully)
            val text = ByteArray(header[SECTIONS - 1]).also(input::readFully)
            return TaggedTexts.read(meta, text)
        }

        fun readHeader(input: DataInputStream): IntArray {
            if (input.readInt() != MAGIC) throw IOException("Not a unicode data file")
            if (input.readInt() != VERSION) throw IOException("Unknown unicode data version")
            val checksum = input.readInt()
            if (input.readInt() != SECTIONS) throw IOException("Unknown unicode data layout")
            val header = IntArray(SECTIONS + 1)
            for (i in 0 until SECTIONS) header[i] = input.readInt()
            header[SECTIONS] = checksum
            return header
        }

        private fun readRuns(section: ByteArray): Pair<IntArray, ByteArray> {
            val reader = VarintReader(section)
            val runs = reader.next()
            val ends = IntArray(runs)
            val values = ByteArray(runs)
            var end = 0
            for (i in 0 until runs) {
                values[i] = reader.byte()
                end += reader.next()
                ends[i] = end
            }
            return ends to values
        }

        private fun readSpans(section: ByteArray): Pair<IntArray, IntArray> {
            val reader = VarintReader(section)
            val size = reader.next()
            val starts = IntArray(size)
            val ends = IntArray(size)
            var last = 0
            for (i in 0 until size) {
                last += reader.next()
                starts[i] = last
                ends[i] = last + reader.next()
            }
            return starts to ends
        }
    }
}

class UnicodeDataReader(stream: InputStream) {
    private val input = DataInputStream(stream)
    private val header = UnicodeData.readHeader(input)
    private var position = 0
    val checksum: Int get() = header[header.size - 1]

    fun data(): UnicodeData {
        check(position == 0)
        position = 1
        return UnicodeData.read(header, input)
    }

    fun meanings(): TaggedTexts {
        if (position == 0) {
            var skip = 0L
            for (i in 0 until header.size - 3) skip += header[i]
            val buffer = ByteArray(1 shl 14)
            while (skip > 0) {
                val read = input.read(buffer, 0, minOf(skip, buffer.size.toLong()).toInt())
                if (read < 0) throw IOException("Unexpected end of data")
                skip -= read
            }
        }
        check(position < 2)
        position = 2
        return UnicodeData.readMeanings(header, input)
    }
}

object HangulNames {
    const val FIRST = 0xAC00
    const val V_COUNT = 21
    const val T_COUNT = 28

    // https://www.unicode.org/Public/18.0.0/ucd/Jamo.txt
    val L = arrayOf("G", "GG", "N", "D", "DD", "R", "M", "B", "BB", "S", "SS", "", "J", "JJ", "C", "K", "T", "P", "H")
    val V = arrayOf("A", "AE", "YA", "YAE", "EO", "E", "YEO", "YE", "O", "WA", "WAE", "OE", "YO", "U", "WEO", "WE", "WI", "YU", "EU", "YI", "I")
    val T = arrayOf("", "G", "GG", "GS", "N", "NJ", "NH", "D", "L", "LG", "LM", "LB", "LS", "LT", "LP", "LH", "M", "B", "BS", "S", "SS", "NG", "J", "C", "K", "T", "P", "H")

    fun name(codePoint: Int): String {
        val s = codePoint - FIRST
        return L[s / (V_COUNT * T_COUNT)] + V[s / T_COUNT % V_COUNT] + T[s % T_COUNT]
    }
}
