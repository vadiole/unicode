package vadiole.unicode.search

import java.text.Normalizer

class SearchTokens(private val maxCount: Int, private val symbols: Boolean) {
    var bytes = ByteArray(256)
        private set
    var start = IntArray(16)
        private set
    var length = IntArray(16)
        private set
    var count = 0
        private set
    private var size = 0

    fun set(text: CharSequence) {
        count = 0
        size = 0
        var index = 0
        var chunkStart = 0
        var chunkTokens = 0
        var chunkEnd = if (symbols) symbolicEnd(text, 0) else 0
        var inWord = false
        while (true) {
            val cp = if (index < text.length) Character.codePointAt(text, index) else -1
            var type = if (cp < 0) SPACE else typeOf(cp)
            if (type == SYMBOL && index < chunkEnd) type = WORD
            if (type == WORD) {
                if (!inWord) {
                    if (count == maxCount) return
                    begin()
                    inWord = true
                    chunkTokens++
                }
                append(fold(cp))
            } else if (type != MARK) {
                if (inWord) {
                    end()
                    inWord = false
                }
                if (type == SPACE) {
                    if (symbols && chunkTokens == 0 && index > chunkStart && count < maxCount) {
                        begin()
                        var i = chunkStart
                        while (i < index) {
                            val symbol = Character.codePointAt(text, i)
                            if (typeOf(symbol) == SYMBOL) append(symbol)
                            i += Character.charCount(symbol)
                        }
                        end()
                    }
                    if (cp < 0) return
                    chunkStart = index + Character.charCount(cp)
                    chunkTokens = 0
                    if (symbols) chunkEnd = symbolicEnd(text, chunkStart)
                }
            }
            index += Character.charCount(cp)
        }
    }

    fun setSplit(source: SearchTokens, at: Int) {
        val from = source.start[0]
        val total = source.length[0]
        if (bytes.size < total) bytes = ByteArray(source.bytes.size)
        System.arraycopy(source.bytes, from, bytes, 0, total)
        start[0] = 0
        length[0] = at
        start[1] = at
        length[1] = total - at
        count = 2
        size = total
    }

    fun isLetters(index: Int): Boolean {
        val from = start[index]
        for (i in from until from + length[index]) {
            val b = bytes[i].toInt()
            if (b < 'a'.code || b > 'z'.code) return false
        }
        return true
    }

    fun token(index: Int) = String(bytes, start[index], length[index], Charsets.UTF_8)

    fun isHex(index: Int): Boolean {
        val from = start[index]
        for (i in from until from + length[index]) {
            val b = bytes[i].toInt()
            if (!(b >= '0'.code && b <= '9'.code) && !(b >= 'a'.code && b <= 'f'.code)) return false
        }
        return true
    }

    private fun begin() {
        if (count == start.size) {
            start = start.copyOf(count * 2)
            length = length.copyOf(count * 2)
        }
        start[count] = size
    }

    private fun end() {
        val from = start[count]
        if (size == from) return
        for (other in 0 until count) {
            if (length[other] == size - from && equal(start[other], from, size - from)) {
                size = from
                return
            }
        }
        length[count++] = size - from
    }

    private fun equal(a: Int, b: Int, length: Int): Boolean {
        for (i in 0 until length) if (bytes[a + i] != bytes[b + i]) return false
        return true
    }

    private fun append(cp: Int) {
        if (size + 4 > bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        if (cp < 0x80) {
            bytes[size++] = cp.toByte()
        } else if (cp < 0x800) {
            bytes[size++] = (0xC0 or (cp shr 6)).toByte()
            bytes[size++] = (0x80 or (cp and 0x3F)).toByte()
        } else if (cp < 0x10000) {
            bytes[size++] = (0xE0 or (cp shr 12)).toByte()
            bytes[size++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
            bytes[size++] = (0x80 or (cp and 0x3F)).toByte()
        } else {
            bytes[size++] = (0xF0 or (cp shr 18)).toByte()
            bytes[size++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
            bytes[size++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
            bytes[size++] = (0x80 or (cp and 0x3F)).toByte()
        }
    }

    private fun symbolicEnd(text: CharSequence, start: Int): Int {
        var index = start
        var digits = false
        var marks = false
        while (index < text.length) {
            val cp = Character.codePointAt(text, index)
            when (typeOf(cp)) {
                SPACE -> break
                SYMBOL -> marks = true
                WORD -> if (Character.isDigit(cp)) digits = true else return start
            }
            index += Character.charCount(cp)
        }
        return if (digits && marks) index else start
    }

    companion object {
        const val WORD = 0
        const val SPACE = 1
        const val SYMBOL = 2
        const val MARK = 3
        private const val LATIN_FIRST = 0xC0
        private const val LATIN_LAST = 0x24F
        private const val LATIN_ADDITIONAL_FIRST = 0x1E00
        private const val LATIN_ADDITIONAL_LAST = 0x1EFF
        private val latin = baseLetters(LATIN_FIRST, LATIN_LAST)
        private val latinAdditional = baseLetters(LATIN_ADDITIONAL_FIRST, LATIN_ADDITIONAL_LAST)

        fun typeOf(cp: Int): Int {
            if (cp < 0x80) {
                return when {
                    (cp >= 'a'.code && cp <= 'z'.code) || (cp >= 'A'.code && cp <= 'Z'.code) || (cp >= '0'.code && cp <= '9'.code) -> WORD
                    cp <= ' '.code || cp == 0x7F -> SPACE
                    else -> SYMBOL
                }
            }
            return when (Character.getType(cp).toByte()) {
                Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
                Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> WORD

                Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.CONTROL -> SPACE
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> MARK
                else -> SYMBOL
            }
        }

        fun fold(cp: Int): Int {
            if (cp < 0x80) return if (cp >= 'A'.code && cp <= 'Z'.code) cp + 32 else cp
            val lower = Character.toLowerCase(cp)
            return when (lower) {
                in LATIN_FIRST..LATIN_LAST -> latin[lower - LATIN_FIRST].code
                in LATIN_ADDITIONAL_FIRST..LATIN_ADDITIONAL_LAST -> latinAdditional[lower - LATIN_ADDITIONAL_FIRST].code
                else -> lower
            }
        }

        private fun baseLetters(first: Int, last: Int) = CharArray(last - first + 1) { offset ->
            val char = Character.toLowerCase((first + offset).toChar())
            val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)[0]
            if (base.code < 0x80 && base.isLetter()) base else char
        }
    }
}
