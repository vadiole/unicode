package vadiole.unicode.search

import java.text.Normalizer

class SearchTokens(private val maxCount: Int, private val distinct: Boolean) {
    var bytes = ByteArray(256)
        private set
    var start = IntArray(16)
        private set
    var length = IntArray(16)
        private set
    var count = 0
        private set
    var sequence = IntArray(16)
        private set
    var sequenceCount = 0
        private set
    private var size = 0
    private var last = 0
    private var lastAt = 0

    fun set(text: String) {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        set(utf8, 0, utf8.size)
    }

    fun set(text: ByteArray, from: Int, to: Int) {
        count = 0
        sequenceCount = 0
        size = 0
        var inWord = false
        var index = from
        while (index < to) {
            val b = text[index].toInt()
            if (b >= 0) {
                index++
                val letter = (b >= 'a'.code && b <= 'z'.code) || (b >= '0'.code && b <= '9'.code)
                if (letter || (b >= 'A'.code && b <= 'Z'.code)) {
                    if (!inWord) {
                        if (count == maxCount) return
                        begin()
                        inWord = true
                    }
                    if (size == bytes.size) bytes = bytes.copyOf(size * 2)
                    last = b
                    bytes[size++] = (if (letter) b else b + 32).toByte()
                } else if (inWord) {
                    end()
                    inWord = false
                }
                continue
            }
            val cp = codePointAt(text, index)
            index += byteCount(b)
            val type = typeOf(cp)
            if (type == WORD) {
                if (cp == HALF_VOICED || cp == HALF_SEMI_VOICED) {
                    if (inWord) voice(cp - HALF_VOICED)
                    continue
                }
                if (!inWord) {
                    if (count == maxCount) return
                    begin()
                    inWord = true
                }
                val folded = fold(cp)
                append(folded and FIRST_MASK)
                if (folded ushr SECOND_SHIFT != 0) append(folded ushr SECOND_SHIFT)
            } else if (type == MARK) {
                if (inWord) mark(cp)
            } else if (inWord) {
                end()
                inWord = false
            }
        }
        if (inWord) end()
    }

    fun setSymbols(text: ByteArray, from: Int, to: Int): Boolean {
        count = 0
        sequenceCount = 0
        size = 0
        var symbols = false
        var index = from
        begin()
        while (index < to) {
            val b = text[index].toInt()
            val cp = if (b >= 0) b else codePointAt(text, index)
            index += if (b >= 0) 1 else byteCount(b)
            when (typeOf(cp)) {
                SYMBOL -> symbols = true
                WORD -> if (!Character.isDigit(cp)) return false
                SPACE -> return false
            }
            append(cp)
        }
        if (!symbols) return false
        end()
        return true
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
        sequence[0] = 0
        sequence[1] = 1
        count = 2
        sequenceCount = 2
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

    fun equals(index: Int, word: ByteArray): Boolean {
        if (length[index] != word.size) return false
        val from = start[index]
        for (i in word.indices) if (bytes[from + i] != word[i]) return false
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
        var token = count
        if (distinct) {
            for (other in 0 until count) {
                if (length[other] == size - from && equal(start[other], from, size - from)) {
                    size = from
                    token = other
                    break
                }
            }
        }
        if (token == count) length[count++] = size - from
        if (sequenceCount == sequence.size) sequence = sequence.copyOf(sequenceCount * 2)
        sequence[sequenceCount++] = token
    }

    private fun equal(a: Int, b: Int, length: Int): Boolean {
        for (i in 0 until length) if (bytes[a + i] != bytes[b + i]) return false
        return true
    }

    private fun mark(cp: Int) {
        val base = last
        if (base < 0x80) return
        when (cp) {
            VOICED, SEMI_VOICED -> voice(cp - VOICED)
            in ACCENT_FIRST..ACCENT_LAST -> {
                val composed = Normalizer.normalize(String(Character.toChars(base)) + cp.toChar(), Normalizer.Form.NFC)
                if (composed.codePointCount(0, composed.length) == 1) replaceLast(composed.codePointAt(0))
            }
            in SCRIPT_MARK_FIRST..SCRIPT_MARK_LAST -> append(cp)
        }
    }

    private fun voice(semi: Int) {
        val base = last
        val voiced = when {
            semi == 0 && base == 0x3046 -> 0x3094
            semi == 0 && base == 0x309D -> 0x309E
            semi == 0 && (base in 0x304B..0x3061 && base and 1 == 1 || base in 0x3064..0x3068 && base and 1 == 0) -> base + 1
            base in 0x306F..0x307B && (base - 0x306F) % 3 == 0 -> base + 1 + semi
            else -> return
        }
        replaceLast(voiced)
    }

    private fun replaceLast(cp: Int) {
        size = lastAt
        append(cp)
    }

    private fun append(cp: Int) {
        if (size + 4 > bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        last = cp
        lastAt = size
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

    companion object {
        const val WORD = 0
        const val SPACE = 1
        const val SYMBOL = 2
        const val MARK = 3
        const val SECOND_SHIFT = 21
        const val FIRST_MASK = (1 shl SECOND_SHIFT) - 1
        private const val VOICED = 0x3099
        private const val SEMI_VOICED = 0x309A
        private const val HALF_VOICED = 0xFF9E
        private const val HALF_SEMI_VOICED = 0xFF9F
        private const val ACCENT_FIRST = 0x300
        private const val ACCENT_LAST = 0x36F
        const val SCRIPT_MARK_FIRST = 0x900
        const val SCRIPT_MARK_LAST = 0x109F
        private const val KANA_SHIFT = 0x60
        private const val FULL_WIDTH_SHIFT = 0xFEE0
        private const val HALF_KANA_FIRST = 0xFF66
        private const val HALF_KANA_LAST = 0xFF9D
        private const val SHARP_S = 's'.code or ('s'.code shl SECOND_SHIFT)
        private const val AE = 'a'.code or ('e'.code shl SECOND_SHIFT)
        private const val OE = 'o'.code or ('e'.code shl SECOND_SHIFT)
        private const val UNDECOMPOSED = "øođdħhıiłl"
        private const val LATIN_FIRST = 0xC0
        private const val LATIN_LAST = 0x24F
        private const val LATIN_ADDITIONAL_FIRST = 0x1E00
        private const val LATIN_ADDITIONAL_LAST = 0x1EFF
        private val latin = baseLetters(LATIN_FIRST, LATIN_LAST)
        private val latinAdditional = baseLetters(LATIN_ADDITIONAL_FIRST, LATIN_ADDITIONAL_LAST)
        private val halfKana = CharArray(HALF_KANA_LAST - HALF_KANA_FIRST + 1) { offset ->
            val full = Normalizer.normalize((HALF_KANA_FIRST + offset).toChar().toString(), Normalizer.Form.NFKC)[0]
            if (full.code in 0x30A1..0x30F6) full - KANA_SHIFT else full
        }

        fun byteCount(lead: Int): Int {
            val b = lead and 0xFF
            return if (b < 0x80) 1 else if (b < 0xE0) 2 else if (b < 0xF0) 3 else 4
        }

        fun codePointAt(text: ByteArray, index: Int): Int {
            val b = text[index].toInt() and 0xFF
            return when {
                b < 0x80 -> b
                b < 0xE0 -> ((b and 0x1F) shl 6) or (text[index + 1].toInt() and 0x3F)
                b < 0xF0 -> ((b and 0x0F) shl 12) or ((text[index + 1].toInt() and 0x3F) shl 6) or (text[index + 2].toInt() and 0x3F)
                else -> ((b and 0x07) shl 18) or ((text[index + 1].toInt() and 0x3F) shl 12) or
                        ((text[index + 2].toInt() and 0x3F) shl 6) or (text[index + 3].toInt() and 0x3F)
            }
        }

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
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK, Character.FORMAT -> MARK
                else -> SYMBOL
            }
        }

        fun fold(cp: Int): Int {
            if (cp < 0x80) return if (cp >= 'A'.code && cp <= 'Z'.code) cp + 32 else cp
            val lower = Character.toLowerCase(cp)
            return when (lower) {
                0xDF -> SHARP_S
                0xE6 -> AE
                0x153 -> OE
                in LATIN_FIRST..LATIN_LAST -> latin[lower - LATIN_FIRST].code
                in LATIN_ADDITIONAL_FIRST..LATIN_ADDITIONAL_LAST -> latinAdditional[lower - LATIN_ADDITIONAL_FIRST].code
                0x3C2 -> 0x3C3
                in 0x30A1..0x30F6, 0x30FD, 0x30FE -> lower - KANA_SHIFT
                in HALF_KANA_FIRST..HALF_KANA_LAST -> halfKana[lower - HALF_KANA_FIRST].code
                in 0xFF10..0xFF19, in 0xFF41..0xFF5A -> lower - FULL_WIDTH_SHIFT
                else -> lower
            }
        }

        private fun baseLetters(first: Int, last: Int) = CharArray(last - first + 1) { offset ->
            val char = Character.toLowerCase((first + offset).toChar())
            val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)[0]
            val plain = UNDECOMPOSED.indexOf(char)
            if (plain >= 0 && plain % 2 == 0) UNDECOMPOSED[plain + 1] else if (base.code < 0x80 && base.isLetter()) base else char
        }
    }
}
