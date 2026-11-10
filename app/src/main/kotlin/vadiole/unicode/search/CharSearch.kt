package vadiole.unicode.search

import java.util.Locale
import vadiole.unicode.data.HangulNames
import vadiole.unicode.data.UnicodeData

class SearchResults(
    val query: String,
    val tokens: Array<String>,
    val codePoints: IntArray,
    val docs: IntArray,
    val total: Int,
    val meaningsStart: Int,
    val codeCount: Int,
    val codeLength: Int,
    val codeExact: Int,
) {
    companion object {
        val EMPTY = SearchResults("", emptyArray(), IntArray(0), IntArray(0), 0, 0, 0, 0, -1)
    }
}

class SearchMatch(val kind: Int, val text: String)

class CharSearch(private val data: UnicodeData, private val index: SearchIndex, private val sources: Array<SearchSource>) {
    var shown: LongArray? = null
    private val queryTokens = SearchTokens(MAX_TOKENS, symbols = true)
    private val splitTokens = SearchTokens(2, symbols = false)
    private var tokens = queryTokens
    private var wholeOnly = false
    private var splitChars = IntArray(64)
    private var splitKeys = IntArray(64)
    private var splitSize = 0
    private val level = ByteArray(data.size) { LEVEL_OBSCURE }
    private var acc = IntArray(0)
    private var cur = IntArray(0)
    private var stamp = ByteArray(0)
    private var touched = IntArray(1024)
    private var touchedSize = 0
    private var gen = 0
    private var first = false
    private var tokenCount = 0
    private var top = LongArray(0)
    private val literals = IntArray(MAX_LITERALS)
    private var literalCount = 0
    private val codeBounds = IntArray(6)
    private var codeBoundCount = 0
    private var codeExact = -1
    private var codeLength = 0
    private var codeExplicit = false

    init {
        for (i in BASIC_AREAS.indices step 2) {
            for (char in data.lowerIndex(BASIC_AREAS[i]) until data.lowerIndex(BASIC_AREAS[i + 1] + 1)) level[char] = LEVEL_BASIC
        }
        for (char in 0 until data.lowerIndex(COMMON_LIMIT)) level[char] = LEVEL_COMMON
        for (codePoint in data.keywords.codePoints) level[data.indexOf(codePoint)] = LEVEL_COMMON
    }

    fun search(query: String, limit: Int): SearchResults {
        literalCount = 0
        codeBoundCount = 0
        codeExact = -1
        codeLength = 0
        codeExplicit = false
        touchedSize = 0
        var text = query
        if (query.isEmpty() || query.codePointCount(0, query.length) != 1) {
            text = trim(query)
            if (text.isEmpty()) return SearchResults.EMPTY
        }
        val single = text.codePointCount(0, text.length) == 1
        if (single) {
            addLiteral(text.codePointAt(0))
            text = trim(text)
        } else {
            parseCode(text)
        }
        var names = false
        if (!codeExplicit) {
            if (!single && codeLength == 0 && hasSymbols(text, 0, text.length)) text = takeLiterals(text)
            queryTokens.set(text)
            names = queryTokens.count > 0
            if (names) {
                matchSplits()
                match()
                mergeSplits()
            }
            if (!single && codeLength == 0 && literalCount == 0 && !hasMatches() && !isAscii(text)) addLiterals(text)
        }
        return collect(query, limit, names)
    }

    fun match(results: SearchResults, position: Int): SearchMatch? {
        val doc = results.docs[position]
        if (doc < 0) return null
        return SearchMatch(index.kind(doc), sources[index.sourceOf(doc)].text(index, doc, results.codePoints[position]))
    }

    private fun trim(text: String): String {
        var start = 0
        var end = text.length
        while (start < end) {
            val cp = text.codePointAt(start)
            if (SearchTokens.typeOf(cp) != SearchTokens.SPACE) break
            start += Character.charCount(cp)
        }
        while (end > start) {
            val cp = text.codePointBefore(end)
            if (SearchTokens.typeOf(cp) != SearchTokens.SPACE) break
            end -= Character.charCount(cp)
        }
        return text.substring(start, end)
    }

    private fun isAscii(text: String): Boolean {
        for (c in text) if (c.code >= 0x80) return false
        return true
    }

    private fun takeLiterals(text: String): String {
        val rest = StringBuilder(text.length)
        var start = 0
        var i = 0
        while (i <= text.length) {
            val cp = if (i < text.length) text.codePointAt(i) else ' '.code
            if (SearchTokens.typeOf(cp) == SearchTokens.SPACE) {
                if (hasSymbols(text, start, i)) {
                    var j = start
                    while (j < i) {
                        addLiteral(text.codePointAt(j))
                        j += Character.charCount(text.codePointAt(j))
                    }
                } else {
                    rest.append(text, start, i).append(' ')
                }
                start = i + Character.charCount(cp)
            }
            i += Character.charCount(cp)
        }
        return rest.toString()
    }

    private fun hasSymbols(text: String, start: Int, end: Int): Boolean {
        var i = start
        while (i < end) {
            val cp = text.codePointAt(i)
            if (cp >= 0x80 && SearchTokens.typeOf(cp) >= SearchTokens.SYMBOL) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun addLiterals(text: String) {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (SearchTokens.typeOf(cp) != SearchTokens.SPACE) addLiteral(cp)
            i += Character.charCount(cp)
        }
    }

    private fun addLiteral(codePoint: Int) {
        val char = data.indexOf(codePoint)
        if (char < 0 || !isShown(char) || literalCount == MAX_LITERALS) return
        for (i in 0 until literalCount) if (literals[i] == char) return
        literals[literalCount++] = char
    }

    private fun isShown(char: Int): Boolean {
        val shown = shown ?: return true
        return shown[char ushr 6] and (1L shl char) != 0L
    }

    private fun parseCode(input: String) {
        val length = input.length
        var from = 0
        if (length >= 2 && ((input[0] == 'U' || input[0] == 'u') && input[1] == '+' ||
                    input[0] == '0' && (input[1] == 'x' || input[1] == 'X') ||
                    input[0] == '\\' && (input[1] == 'u' || input[1] == 'U'))
        ) {
            codeExplicit = true
            from = 2
        } else if (input[0] == 'u' || input[0] == 'U') {
            from = 1
        } else if (length < 2) {
            return
        }
        val digits = length - from
        var value = 0
        var hasDigit = false
        var valid = digits in 1..8
        var i = from
        while (valid && i < length) {
            val digit = Character.digit(input[i++], 16)
            if (digit < 0 || input[i - 1].code >= 0x80) valid = false
            if (digit in 0..9) hasDigit = true
            value = value * 16 + digit
            if (value > Character.MAX_CODE_POINT) valid = false
        }
        if (!valid || (!codeExplicit && !hasDigit)) {
            codeExplicit = false
            return
        }
        codeLength = digits
        val type = Character.getType(value).toByte()
        if (type == Character.PRIVATE_USE || type == Character.SURROGATE) return
        codeExact = data.indexOf(value).let { if (it >= 0 && isShown(it)) it else -1 }
        val leadingZero = input[from] == '0'
        var width = maxOf(4, digits + 1)
        while (width <= 6) {
            if (width > 4 && leadingZero) break
            val shift = 4 * (width - digits)
            val low = value.toLong() shl shift
            if (low > Character.MAX_CODE_POINT) break
            val high = minOf(low + (1L shl shift) - 1, Character.MAX_CODE_POINT.toLong())
            codeBounds[codeBoundCount * 2] = data.lowerIndex(low.toInt())
            codeBounds[codeBoundCount * 2 + 1] = data.lowerIndex(high.toInt() + 1)
            codeBoundCount++
            width++
        }
    }

    private fun isPrefix(char: Int): Boolean {
        for (i in 0 until literalCount) if (literals[i] == char) return true
        if (char == codeExact) return true
        for (i in 0 until codeBoundCount) if (char >= codeBounds[i * 2] && char < codeBounds[i * 2 + 1]) return true
        return false
    }

    private fun hasMatches(): Boolean {
        for (k in 0 until touchedSize) if (isShown(touched[k])) return true
        return false
    }

    private fun collect(query: String, limit: Int, names: Boolean): SearchResults {
        var codeCount = if (codeExact >= 0) 1 else 0
        for (i in 0 until codeBoundCount) {
            for (char in codeBounds[i * 2] until codeBounds[i * 2 + 1]) if (char != codeExact && isShown(char)) codeCount++
        }
        if (limit > top.size) top = LongArray(limit)
        val top = if (limit > 0) top else LongArray(touchedSize)
        val capacity = if (limit > 0) limit else top.size
        var topSize = 0
        var ranked = 0
        var meanings = 0
        for (k in 0 until touchedSize) {
            val char = touched[k]
            if (!isShown(char) || isPrefix(char)) continue
            val packed = acc[char] xor Int.MIN_VALUE
            ranked++
            if (packed ushr TIER_SHIFT >= TIER_MEANING) meanings++
            val key = ((packed ushr DOC_BITS).toLong() shl 40) or (char.toLong() shl DOC_BITS) or (packed and DOC_MASK).toLong()
            if (limit <= 0) {
                top[topSize++] = key
                continue
            }
            var n = topSize
            if (n == capacity) {
                if (key >= top[n - 1]) continue
                n--
            }
            while (n > 0 && top[n - 1] > key) {
                top[n] = top[n - 1]
                n--
            }
            top[n] = key
            if (topSize < capacity) topSize++
        }
        if (limit <= 0) java.util.Arrays.sort(top, 0, topSize)
        val prefix = literalCount + codeCount
        val total = prefix + ranked
        val size = if (limit > 0) minOf(limit, total) else total
        val codePoints = IntArray(size)
        val docs = IntArray(size)
        var n = 0
        for (i in 0 until literalCount) if (n < size) put(codePoints, docs, n++, literals[i], -1)
        if (codeExact >= 0 && n < size) put(codePoints, docs, n++, codeExact, -1)
        for (i in 0 until codeBoundCount) {
            var char = codeBounds[i * 2]
            while (n < size && char < codeBounds[i * 2 + 1]) {
                if (char != codeExact && isShown(char)) put(codePoints, docs, n++, char, -1)
                char++
            }
        }
        var k = 0
        while (n < size) {
            val key = top[k++]
            put(codePoints, docs, n++, ((key ushr DOC_BITS) and CHAR_MASK).toInt(), (key and DOC_MASK.toLong()).toInt())
        }
        val words = if (names) Array(tokens.count) { tokens.token(it).uppercase(Locale.ROOT) } else emptyArray()
        val exact = if (codeExact >= 0) data.codePointAt(codeExact) else -1
        return SearchResults(query, words, codePoints, docs, total, total - meanings, codeCount, codeLength, exact)
    }

    private fun put(codePoints: IntArray, docs: IntArray, position: Int, char: Int, doc: Int) {
        codePoints[position] = data.codePointAt(char)
        docs[position] = doc
    }

    private fun matchSplits() {
        splitSize = 0
        val length = queryTokens.length[0]
        if (queryTokens.count != 1 || length < MIN_COMPOUND || !queryTokens.isLetters(0)) return
        tokens = splitTokens
        wholeOnly = true
        for (at in MIN_COMPOUND_PART..length - MIN_COMPOUND_PART) {
            splitTokens.setSplit(queryTokens, at)
            if (!isWord(0, at) || !isWord(at, length - at)) continue
            touchedSize = 0
            match()
            if (splitSize + touchedSize > splitChars.size) {
                splitChars = splitChars.copyOf(maxOf(splitChars.size * 2, splitSize + touchedSize))
                splitKeys = splitKeys.copyOf(splitChars.size)
            }
            for (k in 0 until touchedSize) {
                splitChars[splitSize] = touched[k]
                splitKeys[splitSize++] = acc[touched[k]]
            }
        }
        touchedSize = 0
        tokens = queryTokens
        wholeOnly = false
    }

    private fun mergeSplits() {
        val g = gen.toByte()
        for (i in 0 until splitSize) {
            val char = splitChars[i]
            if (stamp[char] == g) {
                if (splitKeys[i] < acc[char]) acc[char] = splitKeys[i]
                continue
            }
            stamp[char] = g
            acc[char] = splitKeys[i]
            if (touchedSize == touched.size) touched = touched.copyOf(touchedSize * 2)
            touched[touchedSize++] = char
        }
    }

    private fun isWord(start: Int, length: Int): Boolean {
        val word = lowerBound(start, length)
        return word < index.words && index.wordOff[word + 1] - 1 - index.wordOff[word] == length
    }

    private fun match() {
        if (acc.isEmpty()) {
            acc = IntArray(data.size)
            cur = IntArray(data.size)
            stamp = ByteArray(data.size)
        }
        tokenCount = tokens.count
        if (gen + tokenCount >= Byte.MAX_VALUE) {
            stamp.fill(0)
            gen = 0
        }
        val wordBytes = index.wordBytes
        val wordOff = index.wordOff
        val words = index.words
        val bytes = tokens.bytes
        for (t in 0 until tokenCount) {
            first = t == 0
            gen++
            val start = tokens.start[t]
            val length = tokens.length[t]
            var word = lowerBound(start, length)
            while (word < words) {
                val wordStart = wordOff[word]
                val wordLength = wordOff[word + 1] - 1 - wordStart
                if (wordLength < length) break
                var i = 0
                while (i < length && wordBytes[wordStart + i] == bytes[start + i]) i++
                if (i < length) break
                if (wholeOnly && wordLength > length) break
                postings(word, if (wordLength == length) WHOLE else PREFIX)
                word++
            }
            if (wholeOnly) {
                finishToken(t)
                continue
            }
            if (charCount(start, length) >= MIN_INFIX) {
                val firstByte = bytes[start]
                val last = wordBytes.size - length
                var i = 0
                word = 0
                while (i <= last) {
                    if (wordBytes[i] != firstByte) {
                        i++
                        continue
                    }
                    var j = 1
                    while (j < length && wordBytes[i + j] == bytes[start + j]) j++
                    if (j < length) {
                        i++
                        continue
                    }
                    while (wordOff[word + 1] <= i) word++
                    if (i != wordOff[word]) postings(word, INFIX)
                    i = wordOff[word + 1]
                }
            }
            if (tokens.isHex(t)) matchHexNames(start, length)
            matchHangulNames(start, length)
            finishToken(t)
        }
    }

    private fun finishToken(t: Int) {
        val g = gen.toByte()
        if (t == 0) {
            for (k in 0 until touchedSize) {
                val char = touched[k]
                acc[char] = cur[char]
            }
        } else {
            var kept = 0
            for (k in 0 until touchedSize) {
                val char = touched[k]
                if (stamp[char] == g) {
                    if (cur[char] > acc[char]) acc[char] = cur[char]
                    touched[kept++] = char
                }
            }
            touchedSize = kept
        }
    }

    private fun charCount(start: Int, length: Int): Int {
        var count = 0
        for (i in start until start + length) if (tokens.bytes[i].toInt() and 0xC0 != 0x80) count++
        return count
    }

    private fun lowerBound(start: Int, length: Int): Int {
        val wordBytes = index.wordBytes
        val wordOff = index.wordOff
        val bytes = tokens.bytes
        var low = 0
        var high = index.words
        while (low < high) {
            val mid = (low + high) ushr 1
            val wordStart = wordOff[mid]
            val wordLength = wordOff[mid + 1] - 1 - wordStart
            val n = if (wordLength < length) wordLength else length
            var c = 0
            var i = 0
            while (i < n) {
                c = (wordBytes[wordStart + i].toInt() and 0xFF) - (bytes[start + i].toInt() and 0xFF)
                if (c != 0) break
                i++
            }
            if (c == 0) c = wordLength - length
            if (c < 0) low = mid + 1 else high = mid
        }
        return low
    }

    private fun postings(word: Int, quality: Int) {
        val post = index.postBytes
        val docChar = index.docChar
        val docMeta = index.docMeta
        val docWords = index.docWords
        var p = index.postOff[word]
        val end = index.postOff[word + 1]
        var doc = -1
        while (p < end) {
            var delta = 0
            var shift = 0
            while (true) {
                val b = post[p++].toInt()
                delta = delta or ((b and 0x7F) shl shift)
                if (b >= 0) break
                shift += 7
            }
            doc += delta ushr SearchIndex.POSITION_BITS
            val position = delta and SearchIndex.POSITION_MASK
            val meta = docMeta[doc].toInt()
            val char = docChar[doc]
            if (meta and SearchIndex.SPAN == 0) {
                hit(char, doc, meta, docWords[doc].toInt(), quality, position)
            } else {
                val wordCount = docWords[doc].toInt()
                for (c in char..index.spanLast(doc)) hit(c, doc, meta, wordCount, quality, position)
            }
        }
    }

    private fun hit(char: Int, doc: Int, meta: Int, words: Int, quality: Int, position: Int) {
        val g = gen.toByte()
        val seen = stamp[char]
        if (seen != g && !first && seen != (gen - 1).toByte()) return
        val kind = meta and SearchIndex.KIND_MASK
        val level = level[char].toInt()
        val exact = quality == WHOLE && words == tokenCount && meta and SearchIndex.SHORT == 0 && (kind != SearchKind.ALIAS || level == 0)
        var tier = TIERS[GROUPS[kind] * 4 + if (exact) EXACT else quality].toInt()
        var flag = level
        var length = if (level == 2) words else 0
        if (tier == TIER_NAME_WORD || tier == TIER_NAME_PREFIX) {
            flag = if (position and SearchIndex.LAST_WORD != 0) 0 else if (position and SearchIndex.FIRST_WORD != 0) 1 else 2
        }
        if (level == 2 && tier in TIER_KNOWN_FIRST..TIER_KNOWN_LAST) tier += TIER_KNOWN_LAST - TIER_KNOWN_FIRST + 1
        if (kind == SearchKind.MEANING) {
            flag = (meta shr SearchIndex.LEVEL_SHIFT) and 3
            length = words
        }
        val key = ((tier shl TIER_SHIFT) or (flag shl 25) or (length shl DOC_BITS) or doc) xor Int.MIN_VALUE
        if (seen == g) {
            if (key < cur[char]) cur[char] = key
            return
        }
        stamp[char] = g
        cur[char] = key
        if (first) {
            if (touchedSize == touched.size) touched = touched.copyOf(touchedSize * 2)
            touched[touchedSize++] = char
        }
    }

    private fun matchHexNames(start: Int, length: Int) {
        if (length > 6) return
        var value = 0
        for (i in start until start + length) value = value * 16 + Character.digit(tokens.bytes[i].toInt(), 16)
        for (width in maxOf(4, length)..5) {
            val shift = 4 * (width - length)
            val low = value shl shift
            val high = low + (1 shl shift) - 1
            if (width == 5 && low < 0x10000) continue
            for (range in 0 until data.rangeCount) {
                if (data.isHangulRange(range)) continue
                val from = maxOf(low, data.rangeFirst[range])
                val to = minOf(high, data.rangeLast[range])
                if (from > to) continue
                val doc = index.rangeDocs[range]
                val meta = index.docMeta[doc].toInt()
                val words = index.docWords[doc].toInt()
                val quality = if (width == length) WHOLE else PREFIX
                val offset = data.indexOf(data.rangeFirst[range]) - data.rangeFirst[range]
                for (codePoint in from..to) hit(codePoint + offset, doc, meta, words, quality, SearchIndex.LAST_WORD)
            }
        }
    }

    private fun matchHangulNames(start: Int, length: Int) {
        if (length > HANGUL_NAME_MAX) return
        var range = 0
        while (range < data.rangeCount && !data.isHangulRange(range)) range++
        if (range == data.rangeCount) return
        val doc = index.rangeDocs[range]
        val meta = index.docMeta[doc].toInt()
        val words = index.docWords[doc].toInt()
        val offset = data.indexOf(HangulNames.FIRST)
        val end = start + length
        for (l in HANGUL_L.indices) {
            val afterL = matchPart(start, end, HANGUL_L[l])
            if (afterL < 0) continue
            for (v in HANGUL_V.indices) {
                val afterV = matchPart(afterL, end, HANGUL_V[v])
                if (afterV < 0) continue
                for (t in HANGUL_T.indices) {
                    if (matchPart(afterV, end, HANGUL_T[t]) != end) continue
                    val whole = HANGUL_L[l].size + HANGUL_V[v].size + HANGUL_T[t].size == length
                    hit(offset + (l * HangulNames.V_COUNT + v) * HangulNames.T_COUNT + t, doc, meta, words, if (whole) WHOLE else PREFIX, SearchIndex.LAST_WORD)
                }
            }
        }
    }

    private fun matchPart(from: Int, end: Int, part: ByteArray): Int {
        val bytes = tokens.bytes
        val remaining = end - from
        if (remaining <= 0) return end
        val n = minOf(remaining, part.size)
        for (i in 0 until n) if (bytes[from + i] != part[i]) return -1
        return if (remaining <= part.size) end else from + part.size
    }

    private companion object {
        const val MAX_TOKENS = 16
        const val MAX_LITERALS = 16
        const val COMMON_LIMIT = 0x100
        const val MIN_INFIX = 3
        const val MIN_COMPOUND = 6
        const val MIN_COMPOUND_PART = 2
        const val HANGUL_NAME_MAX = 7
        const val EXACT = 0
        const val WHOLE = 1
        const val PREFIX = 2
        const val INFIX = 3
        const val DOC_BITS = 19
        const val DOC_MASK = (1 shl DOC_BITS) - 1
        const val CHAR_MASK = (1L shl 21) - 1
        const val TIER_SHIFT = 27
        const val TIER_NAME_WORD = 3
        const val TIER_NAME_PREFIX = 4
        const val TIER_KNOWN_FIRST = 3
        const val TIER_KNOWN_LAST = 8
        const val LEVEL_COMMON: Byte = 0
        const val LEVEL_BASIC: Byte = 1
        const val LEVEL_OBSCURE: Byte = 2
        val BASIC_AREAS = intArrayOf(0x0100, 0x024F, 0x0370, 0x03FF, 0x0400, 0x04FF, 0x2000, 0x2BFF)
        const val TIER_MEANING = 21
        val GROUPS = byteArrayOf(0, 0, 0, 0, 1, 1, 2, 3, 4, 5)
        val TIERS = byteArrayOf(
            1, 3, 4, 17,
            2, 5, 6, 18,
            7, 8, 8, 19,
            15, 15, 15, 20,
            16, 16, 16, 20,
            21, 22, 23, 24,
        )
        val HANGUL_L = jamo(HangulNames.L)
        val HANGUL_V = jamo(HangulNames.V)
        val HANGUL_T = jamo(HangulNames.T)

        fun jamo(names: Array<String>) = Array(names.size) { names[it].lowercase(Locale.ROOT).toByteArray() }
    }
}
