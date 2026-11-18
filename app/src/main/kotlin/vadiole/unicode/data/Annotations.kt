package vadiole.unicode.data

import java.io.IOException
import java.io.InputStream

class Annotations(val key: String, val codePoints: IntArray, private val starts: IntArray, val bytes: ByteArray) {
    val size: Int get() = codePoints.size

    fun start(index: Int) = starts[index]

    fun end(from: Int): Int {
        var at = from
        while (bytes[at] != TAB && bytes[at] != NEW_LINE) at++
        return at
    }

    fun text(from: Int, to: Int) = String(bytes, from, to - from, Charsets.UTF_8)

    fun name(codePoint: Int): String? {
        val index = codePoints.binarySearch(codePoint)
        if (index < 0) return null
        val start = starts[index]
        val end = end(start)
        return if (end > start) text(start, end) else null
    }

    fun displayKeywords(codePoint: Int): List<String> {
        val index = codePoints.binarySearch(codePoint)
        if (index < 0) return emptyList()
        val start = end(starts[index]) + 1
        val end = end(start)
        return if (end > start) text(start, end).split(SEPARATOR) else emptyList()
    }

    companion object {
        const val FORMAT = 1
        const val NONE = "none"
        const val SEPARATOR = '|'
        const val TAB = '\t'.code.toByte()
        const val NEW_LINE = '\n'.code.toByte()
        private const val MAGIC = "annotations"
        private const val MAX_HEADER = 64
        private const val FIELDS = 3

        fun header(tag: String, version: String) = "$MAGIC $FORMAT $tag $version"

        fun readKey(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (line.length == MAX_HEADER) return null
                line.append(b.toChar())
            }
            val parts = line.split(' ')
            if (parts.size != 4 || parts[0] != MAGIC || parts[1] != FORMAT.toString() || parts[2] == NONE) return null
            return parts[2] + '@' + parts[3]
        }

        fun read(key: String, input: InputStream): Annotations {
            val bytes = input.readBytes()
            var lines = 0
            for (b in bytes) if (b == NEW_LINE) lines++
            val codePoints = IntArray(lines)
            val starts = IntArray(lines)
            var at = 0
            for (line in 0 until lines) {
                var codePoint = 0
                while (bytes[at] != TAB) codePoint = codePoint * 16 + Character.digit(bytes[at++].toInt(), 16)
                if (line > 0 && codePoint <= codePoints[line - 1]) throw IOException("Annotations out of order")
                codePoints[line] = codePoint
                starts[line] = ++at
                var tabs = 1
                while (bytes[at] != NEW_LINE) if (bytes[at++] == TAB) tabs++
                if (tabs != FIELDS) throw IOException("Bad annotation line")
                at++
            }
            if (at != bytes.size) throw IOException("Unterminated annotations")
            return Annotations(key, codePoints, starts, bytes)
        }
    }
}
