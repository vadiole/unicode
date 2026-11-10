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
    val search: CharSearch?,
) {
    fun match(position: Int): SearchMatch? = search?.match(this, position)

    fun kind(position: Int): Int = search?.kind(this, position) ?: SearchKind.NAME

    companion object {
        val EMPTY = SearchResults("", emptyArray(), IntArray(0), IntArray(0), 0, 0, 0, 0, -1, null)
    }
}

class SearchMatch(val kind: Int, val text: String)

class CharSearch(private val data: UnicodeData, private val index: SearchIndex, private val sources: Array<SearchSource>) {
    var shown: LongArray? = null
    private val queryTokens = SearchTokens(MAX_TOKENS, distinct = true)
    private val splitTokens = SearchTokens(2, distinct = true)
    private val symbolTokens = SearchTokens(1, distinct = true)
    private val textTokens = SearchTokens(Int.MAX_VALUE, distinct = false)
    private var tokens = queryTokens
    private var wholeOnly = false
    private val level = ByteArray(data.size) { LEVEL_OTHER }
    private val generated = LongArray((data.size + 63) ushr 6)
    private var acc = IntArray(0)
    private var cur = IntArray(0)
    private var stamp = ByteArray(0)
    private var touched = IntArray(1024)
    private var touchedSize = 0
    private var gen = 0
    private var first = false
    private var typing = 0
    private var unfinished = false
    private var short = false
    private var single = false
    private var tokenCount = 0
    private val tokenLow = IntArray(MAX_TOKENS)
    private val tokenHigh = IntArray(MAX_TOKENS)
    private val tokenOrder = IntArray(MAX_TOKENS)
    private var spanStart = IntArray(256)
    private var spanEnd = IntArray(256)
    private var spanKey = IntArray(256)
    private var spanSorted = LongArray(256)
    private var spanCount = 0
    private var spanChars = 0L
    private var active = IntArray(16)
    private var segmentStart = IntArray(256)
    private var segmentEnd = IntArray(256)
    private var segmentKey = IntArray(256)
    private var segmentCount = 0
    private var exactDocs = IntArray(64)
    private var exactCount = 0
    private var savedChars = IntArray(64)
    private var savedKeys = IntArray(64)
    private var savedSize = 0
    private var postPosition = 0
    private var postEnd = 0
    private var postDoc = 0
    private var postBits = 0
    private var top = LongArray(0)
    private val literals = IntArray(MAX_LITERALS)
    private var literalCount = 0
    private val codeBounds = IntArray(6)
    private var codeBoundCount = 0
    private var codeExact = -1
    private var codeLength = 0
    private var codeExplicit = false

    init {
        setLevel(KNOWN_AREAS, LEVEL_KNOWN)
        setLevel(COMMON_AREAS, LEVEL_COMMON)
        for (codePoint in data.keywords.codePoints) level[data.indexOf(codePoint)] = LEVEL_COMMON
        setLevel(OTHER_AREAS, LEVEL_OTHER)
        for (range in 0 until data.rangeCount) {
            for (char in data.indexOf(data.rangeFirst[range])..data.indexOf(data.rangeLast[range])) generated[char ushr 6] = generated[char ushr 6] or (1L shl char)
        }
    }

    private fun isListed(char: Int): Boolean {
        return isShown(char) && (codeLength >= MIN_GENERATED_HEX || generated[char ushr 6] and (1L shl char) == 0L)
    }

    private fun setLevel(areas: IntArray, value: Byte) {
        for (i in areas.indices step 2) {
            for (char in data.lowerIndex(areas[i]) until data.lowerIndex(areas[i + 1] + 1)) level[char] = value
        }
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
            parseCode(text, false)
        }
        var names = false
        if (!codeExplicit) {
            if (!single && codeLength == 0 && hasSymbols(text, 0, text.length)) text = takeLiterals(text)
            val utf8 = paired(text).toByteArray(Charsets.UTF_8)
            queryTokens.set(utf8, 0, utf8.size)
            tokens = queryTokens
            names = queryTokens.count > 0
            if (names) {
                match()
                matchExact()
                matchSplits()
            } else {
                reserve(1)
                gen++
            }
            if (symbolTokens.setSymbols(utf8, 0, utf8.size)) matchSymbols()
            if (!single && codeLength == 0 && literalCount == 0 && !hasMatches()) {
                if (isAscii(text)) parseCode(text, true) else addLiterals(text)
            }
        }
        return collect(query, limit, names)
    }

    fun match(results: SearchResults, position: Int): SearchMatch? {
        val doc = results.docs[position]
        if (doc < 0) return null
        return SearchMatch(index.kind(doc), sources[index.sourceOf(doc)].text(index, doc, results.codePoints[position]))
    }

    fun kind(results: SearchResults, position: Int): Int {
        val doc = results.docs[position]
        return if (doc < 0) SearchKind.NAME else index.kind(doc)
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

    private fun paired(text: String): String {
        var result: StringBuilder? = null
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in SURROGATES) {
                if (result == null) result = StringBuilder(text.length).append(text, 0, i)
            } else {
                result?.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return result?.toString() ?: text
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

    private fun parseCode(input: String, letters: Boolean) {
        if (input.isEmpty()) return
        var length = input.length
        while (length > 0 && input[length - 1] in CODE_TRAILERS) length--
        var from = 0
        if (length >= 2 && ((input[0] == 'U' || input[0] == 'u') && input[1] == '+' ||
                    input[0] == '0' && (input[1] == 'x' || input[1] == 'X') ||
                    input[0] == '\\' && (input[1] == 'u' || input[1] == 'U'))
        ) {
            codeExplicit = true
            from = 2
            while (from < length && input[from] == ' ') from++
        } else if (length != input.length) {
            return
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
        if (!valid || (!codeExplicit && !hasDigit && !letters)) {
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
        for (i in 0 until codeBoundCount) if (char >= codeBounds[i * 2] && char < codeBounds[i * 2 + 1]) return isListed(char)
        return false
    }

    private fun hasMatches(): Boolean {
        for (k in 0 until touchedSize) if (isShown(touched[k])) return true
        return false
    }

    private fun collect(query: String, limit: Int, names: Boolean): SearchResults {
        var codeCount = if (codeExact >= 0) 1 else 0
        for (i in 0 until codeBoundCount) {
            for (char in codeBounds[i * 2] until codeBounds[i * 2 + 1]) if (char != codeExact && isListed(char)) codeCount++
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
            val packed = acc[char]
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
                if (char != codeExact && isListed(char)) put(codePoints, docs, n++, char, -1)
                char++
            }
        }
        var k = 0
        while (n < size) {
            val key = top[k++]
            put(codePoints, docs, n++, ((key ushr DOC_BITS) and CHAR_MASK).toInt(), (key and DOC_MASK.toLong()).toInt())
        }
        val words = if (names) Array(queryTokens.count) { queryTokens.token(it).uppercase(Locale.ROOT) } else emptyArray()
        val exact = if (codeExact >= 0) data.codePointAt(codeExact) else -1
        return SearchResults(query, words, codePoints, docs, total, total - meanings, codeCount, codeLength, exact, this)
    }

    private fun put(codePoints: IntArray, docs: IntArray, position: Int, char: Int, doc: Int) {
        codePoints[position] = data.codePointAt(char)
        docs[position] = doc
    }

    private fun reserve(count: Int) {
        if (acc.isEmpty()) {
            acc = IntArray(data.size)
            cur = IntArray(data.size)
            stamp = ByteArray(data.size)
        }
        if (gen + count >= Byte.MAX_VALUE) {
            stamp.fill(0)
            gen = 0
        }
    }

    private fun merge(char: Int, key: Int) {
        if (stamp[char] == gen.toByte()) {
            if (key < acc[char]) acc[char] = key
            return
        }
        stamp[char] = gen.toByte()
        acc[char] = key
        if (touchedSize == touched.size) touched = touched.copyOf(touchedSize * 2)
        touched[touchedSize++] = char
    }

    private fun matchSymbols() {
        tokens = symbolTokens
        val word = findWord(0)
        tokens = queryTokens
        if (word < 0) return
        openPostings(word)
        while (nextPosting()) merge(index.docChar[postDoc], (TIER_EXACT_ALIAS shl TIER_SHIFT) or postDoc)
    }

    private fun matchSplits() {
        val length = queryTokens.length[0]
        if (queryTokens.sequenceCount != 1 || length !in MIN_SPLIT_FIRST + MIN_SPLIT_SECOND..MAX_SPLIT_LENGTH || !queryTokens.isLetters(0)) return
        savedSize = -1
        tokens = splitTokens
        for (at in MIN_SPLIT_FIRST..length - MIN_SPLIT_SECOND) {
            splitTokens.setSplit(queryTokens, at)
            if (findWord(0) < 0 || findWord(1) < 0) continue
            if (savedSize < 0) {
                savedSize = 0
                save(false)
            }
            wholeOnly = true
            touchedSize = 0
            match()
            matchExact()
            save(true)
        }
        tokens = queryTokens
        wholeOnly = false
        if (savedSize < 0) return
        touchedSize = 0
        reserve(1)
        gen++
        for (i in 0 until savedSize) merge(savedChars[i], savedKeys[i])
    }

    private fun save(split: Boolean) {
        if (savedSize + touchedSize > savedChars.size) {
            savedChars = savedChars.copyOf(maxOf(savedChars.size * 2, savedSize + touchedSize))
            savedKeys = savedKeys.copyOf(savedChars.size)
        }
        for (k in 0 until touchedSize) {
            val key = acc[touched[k]]
            savedChars[savedSize] = touched[k]
            val tier = key ushr TIER_SHIFT
            savedKeys[savedSize++] = when {
                !split -> key
                tier <= TIER_EXACT_ALIAS -> (TIER_WORD shl TIER_SHIFT) or (key and DOC_MASK)
                else -> (TIER_SPLIT shl TIER_SHIFT) or (tier shl DOC_BITS) or (key and DOC_MASK)
            }
        }
    }

    private fun findWord(token: Int): Int {
        val start = tokens.start[token]
        val length = tokens.length[token]
        val word = lowerBound(start, length)
        if (word == index.words || index.wordOff[word + 1] - 1 - index.wordOff[word] != length) return -1
        val from = index.wordOff[word]
        for (i in 0 until length) if (index.wordBytes[from + i] != tokens.bytes[start + i]) return -1
        return word
    }

    private fun openPostings(word: Int) {
        postPosition = index.postOff[word]
        postEnd = index.postOff[word + 1]
        postDoc = -1
    }

    private fun nextPosting(): Boolean {
        if (postPosition >= postEnd) return false
        val post = index.postBytes
        var delta = 0
        var shift = 0
        while (true) {
            val b = post[postPosition++].toInt()
            delta = delta or ((b and 0x7F) shl shift)
            if (b >= 0) break
            shift += 7
        }
        postDoc += delta ushr SearchIndex.POSITION_BITS
        postBits = delta and SearchIndex.POSITION_MASK
        return true
    }

    private fun matchExact() {
        val count = tokens.sequenceCount
        var word = findWord(tokens.sequence[0])
        if (word < 0) return
        val docMeta = index.docMeta
        val docWords = index.docWords
        exactCount = 0
        openPostings(word)
        while (nextPosting()) {
            val words = docWords[postDoc].toInt()
            if (postBits and SearchIndex.FIRST_WORD == 0 || words > count || (count == 1 && words != 1)) continue
            val meta = docMeta[postDoc].toInt()
            if (meta and (SearchIndex.SPAN or SearchIndex.SHORT) != 0 || meta and SearchIndex.KIND_MASK > SearchKind.KEYWORD) continue
            if (exactCount == exactDocs.size) exactDocs = exactDocs.copyOf(exactCount * 2)
            exactDocs[exactCount++] = postDoc
        }
        var middle = 1
        while (exactCount > MAX_VERIFIED && middle < count - 1) {
            word = findWord(tokens.sequence[middle++])
            if (word < 0) return
            var kept = 0
            var k = 0
            openPostings(word)
            while (k < exactCount && nextPosting()) {
                while (k < exactCount && exactDocs[k] < postDoc) k++
                if (k < exactCount && exactDocs[k] == postDoc) exactDocs[kept++] = exactDocs[k++]
            }
            exactCount = kept
        }
        val g = gen.toByte()
        for (k in 0 until exactCount) {
            val doc = exactDocs[k]
            val char = index.docChar[doc]
            if (stamp[char] != g) continue
            val tier = when (docMeta[doc].toInt() and SearchIndex.KIND_MASK) {
                SearchKind.NAME, SearchKind.UNICODE_1, SearchKind.FORMAL_ALIAS -> TIER_EXACT
                SearchKind.ABBREVIATION -> if (isRareControl(data.codePointAt(char))) continue else TIER_EXACT
                SearchKind.SHORT_NAME -> TIER_EXACT_ALIAS
                SearchKind.ALIAS -> if (level[char] == LEVEL_COMMON) TIER_EXACT_ALIAS else continue
                else -> TIER_WORD
            }
            if ((tier shl TIER_SHIFT) >= acc[char]) continue
            val whole = if (count == 1) WHOLE else wholeText(doc, count)
            val order = if (tier == TIER_WORD) 1 else whole - WHOLE
            val key = (tier shl TIER_SHIFT) or (order shl ORDER_SHIFT) or doc
            if (whole != 0 && key < acc[char]) acc[char] = key
        }
    }

    private fun isRareControl(codePoint: Int): Boolean {
        return codePoint in 0x01..0x06 || codePoint in 0x0E..0x1A || codePoint in 0x1C..0x1F || codePoint in 0x80..0x9F
    }

    private fun wholeText(doc: Int, count: Int): Int {
        val text = textTokens
        text.set(sources[index.sourceOf(doc)].text(index, doc, data.codePointAt(index.docChar[doc])))
        if (text.count < count) return 0
        val bytes = tokens.bytes
        var result = WHOLE
        for (i in 0 until count) {
            val token = tokens.sequence[i]
            val length = tokens.length[token]
            if (text.length[i] != length) {
                if (i < count - 1 || wholeOnly || text.length[i] < length || length < MIN_UNFINISHED) return 0
                result = PREFIX
            }
            val from = tokens.start[token]
            val start = text.start[i]
            for (j in 0 until length) if (text.bytes[start + j] != bytes[from + j]) return 0
        }
        for (i in count until text.count) if (!SearchIndex.isClassWord(text, i, SearchIndex.DROPPED_WORDS)) return 0
        return result
    }

    private fun match() {
        tokenCount = tokens.count
        reserve(tokenCount)
        val wordBytes = index.wordBytes
        val wordOff = index.wordOff
        val words = index.words
        val bytes = tokens.bytes
        for (t in 0 until tokenCount) {
            val start = tokens.start[t]
            val length = tokens.length[t]
            val low = lowerBound(start, length)
            var high = low
            if (wholeOnly) {
                if (low < words && wordOff[low + 1] - 1 - wordOff[low] == length) high++
            } else {
                high = prefixEnd(low, start, length)
            }
            tokenLow[t] = low
            tokenHigh[t] = high
            val cost = index.postOff[high] - index.postOff[low]
            var n = t
            while (n > 0 && index.postOff[tokenHigh[tokenOrder[n - 1]]] - index.postOff[tokenLow[tokenOrder[n - 1]]] > cost) {
                tokenOrder[n] = tokenOrder[n - 1]
                n--
            }
            tokenOrder[n] = t
        }
        for (n in 0 until tokenCount) {
            val t = tokenOrder[n]
            first = n == 0
            gen++
            spanCount = 0
            spanChars = 0
            val start = tokens.start[t]
            val length = tokens.length[t]
            typing = if (tokenCount > 1) 0 else if (length >= MIN_TYPING) TYPING_WORD else if (length == MIN_TYPING - 1) TYPING_START else 0
            val chars = charCount(start, length)
            short = chars < MIN_SPAN && bytes[start] >= 0
            single = chars == 1
            unfinished = tokenCount > 1 && t == tokens.sequence[tokens.sequenceCount - 1]
            for (other in 0 until tokenCount) {
                if (other != t && isStartOf(t, tokens.bytes, tokens.start[other], tokens.length[other])) unfinished = false
            }
            for (word in tokenLow[t] until tokenHigh[t]) {
                postings(word, if (wordOff[word + 1] - 1 - wordOff[word] == length) WHOLE else PREFIX)
            }
            if (!wholeOnly) {
                if (chars >= minInfix(start)) {
                    val firstByte = bytes[start]
                    val last = wordBytes.size - length
                    var i = 0
                    var word = 0
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
                if ((length >= MIN_GENERATED_HEX || tokenCount > 1) && tokens.isHex(t)) matchHexNames(start, length)
                matchHangulNames(t, start, length)
            }
            if (spanCount > 0) matchSpans()
            finishToken()
            if (touchedSize == 0) break
        }
    }

    private fun finishToken() {
        val g = gen.toByte()
        if (first) {
            for (k in 0 until touchedSize) {
                val char = touched[k]
                acc[char] = cur[char]
            }
        } else {
            var kept = 0
            for (k in 0 until touchedSize) {
                val char = touched[k]
                if (stamp[char] == g) {
                    val best = acc[char]
                    val next = cur[char]
                    val worst = if (next > best) next else best
                    val words = maxOf(best and WORDS_MASK, next and WORDS_MASK)
                    acc[char] = (worst and WORDS_MASK.inv()) or words
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

    private fun minInfix(start: Int): Int {
        val cp = SearchTokens.codePointAt(tokens.bytes, start)
        return if (cp in KANA_FIRST..KANA_LAST || Character.isIdeographic(cp)) MIN_INFIX_CJK else MIN_INFIX
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

    private fun prefixEnd(from: Int, start: Int, length: Int): Int {
        val wordBytes = index.wordBytes
        val wordOff = index.wordOff
        val bytes = tokens.bytes
        var low = from
        var high = index.words
        while (low < high) {
            val mid = (low + high) ushr 1
            val wordStart = wordOff[mid]
            var matches = wordOff[mid + 1] - 1 - wordStart >= length
            var i = 0
            while (matches && i < length) {
                if (wordBytes[wordStart + i] != bytes[start + i]) matches = false
                i++
            }
            if (matches) low = mid + 1 else high = mid
        }
        return low
    }

    private fun postings(word: Int, quality: Int) {
        val post = index.postBytes
        val docChar = index.docChar
        val docMeta = index.docMeta
        val g = gen.toByte()
        val previous = (gen - 1).toByte()
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
            val meta = docMeta[doc].toInt()
            val char = docChar[doc]
            if (meta and SearchIndex.SPAN != 0) {
                if (short && (quality != WHOLE || tokenCount == 1)) continue
                addSpan(char, doc, meta, quality, delta and SearchIndex.POSITION_MASK)
                continue
            }
            val seen = stamp[char]
            if (seen != g && !first && seen != previous) continue
            var key = key(char, doc, meta, if (unfinished && quality == PREFIX) WHOLE else quality, delta and SearchIndex.POSITION_MASK)
            if (unfinished && quality != WHOLE && key ushr TIER_SHIFT < TIER_MEANING) key = key or (1 shl ORDER_SHIFT)
            if (key >= 0) hit(char, key, seen)
        }
    }

    private fun key(char: Int, doc: Int, meta: Int, quality: Int, position: Int): Int {
        val level = level[char].toInt()
        var tier: Int
        var order = 0
        when (meta and SearchIndex.KIND_MASK) {
            SearchKind.NAME, SearchKind.UNICODE_1, SearchKind.FORMAL_ALIAS -> when (quality) {
                WHOLE -> {
                    tier = if (level == LEVEL_OTHER.toInt()) TIER_WORD_OTHER else TIER_WORD
                    order = if (position != 0) 0 else 1
                }
                PREFIX -> {
                    tier = if (level == LEVEL_OTHER.toInt()) TIER_START_OTHER else TIER_START
                    order = if (position != 0) 0 else 1
                    if (level == LEVEL_COMMON.toInt() && typing != 0 && position != 0 && meta and SearchIndex.KIND_MASK == SearchKind.NAME) {
                        tier = TIER_WORD
                        order = TYPING_WORD - typing
                    }
                }
                else -> tier = TIER_INSIDE_NAME
            }
            SearchKind.ABBREVIATION -> tier = if (quality == INFIX) TIER_INSIDE_ALIAS else TIER_ALIAS_START
            SearchKind.ALIAS, SearchKind.SHORT_NAME -> {
                tier = if (quality == WHOLE) TIER_ALIAS_WORD else if (quality == PREFIX) TIER_ALIAS_START else TIER_INSIDE_ALIAS
                order = if (position != 0) 0 else 1
            }
            SearchKind.KEYWORD -> {
                tier = if (quality == INFIX) TIER_INSIDE_KEYWORD else TIER_KEYWORD
                order = if (quality == WHOLE) 0 else 1
            }
            SearchKind.BLOCK -> tier = if (quality == INFIX) TIER_INSIDE_BLOCK else TIER_BLOCK
            SearchKind.SUBHEAD -> tier = if (quality == INFIX) TIER_INSIDE_BLOCK else TIER_SUBHEAD
            else -> {
                if (wholeOnly || short && !unfinished && (quality != WHOLE || tokenCount == 1 && (single || index.docWords[doc].toInt() != 1))) return -1
                tier = if (quality == WHOLE && index.docWords[doc].toInt() == tokenCount) TIER_MEANING else TIER_MEANING + quality
                order = (meta shr SearchIndex.LEVEL_SHIFT) and 3
            }
        }
        var words = 0
        if (tier >= TIER_MEANING) {
            words = index.docWords[doc].toInt()
        } else if (tokenCount > 1) {
            order = if (meta and SearchIndex.SPAN != 0 && quality != WHOLE) 1 else 0
            if (tier == TIER_WORD || tier == TIER_START) order += level * 2
            if (tier <= TIER_START_OTHER) {
                words = index.docWords[doc].toInt()
                if (meta and SearchIndex.KIND_MASK != SearchKind.NAME && words < SearchIndex.MAX_WORDS) words++
            }
        }
        return (tier shl TIER_SHIFT) or (order shl ORDER_SHIFT) or (words shl DOC_BITS) or doc
    }

    private fun hit(char: Int, key: Int, seen: Byte) {
        if (seen == gen.toByte()) {
            if (key < cur[char]) cur[char] = key
            return
        }
        stamp[char] = gen.toByte()
        cur[char] = key
        if (first) {
            if (touchedSize == touched.size) touched = touched.copyOf(touchedSize * 2)
            touched[touchedSize++] = char
        }
    }

    private fun hitGenerated(char: Int, doc: Int, quality: Int, named: Boolean) {
        val seen = stamp[char]
        if (seen != gen.toByte() && !first && seen != (gen - 1).toByte()) return
        val words = if (tokenCount > 1) index.docWords[doc].toInt() else 0
        val whole = quality == WHOLE || unfinished
        var rank = if (!named) (TIER_GENERATED shl TIER_SHIFT) or ((quality - WHOLE) shl ORDER_SHIFT) else if (whole) TIER_WORD_OTHER shl TIER_SHIFT else TIER_START_OTHER shl TIER_SHIFT
        if (named && quality != WHOLE && unfinished) rank = rank or (1 shl ORDER_SHIFT)
        hit(char, rank or (words shl DOC_BITS) or doc, seen)
    }

    private fun addSpan(char: Int, doc: Int, meta: Int, quality: Int, position: Int) {
        val key = key(char, doc, meta, quality, position)
        if (key < 0) return
        if (spanCount == spanStart.size) {
            spanStart = spanStart.copyOf(spanCount * 2)
            spanEnd = spanEnd.copyOf(spanCount * 2)
            spanKey = spanKey.copyOf(spanCount * 2)
            spanSorted = spanSorted.copyOf(spanCount * 2)
        }
        val last = index.spanLast(doc)
        spanStart[spanCount] = char
        spanEnd[spanCount] = last
        spanKey[spanCount] = key
        spanSorted[spanCount] = (char.toLong() shl 32) or spanCount.toLong()
        spanCount++
        spanChars += last - char + 1
    }

    private fun matchSpans() {
        val scatter = first || spanChars <= touchedSize * PROBE_COST
        java.util.Arrays.sort(spanSorted, 0, spanCount)
        segmentCount = 0
        var activeCount = 0
        var next = 0
        var position = 0
        while (next < spanCount || activeCount > 0) {
            if (activeCount == 0) position = spanStart[spanSorted[next].toInt()]
            while (next < spanCount && spanStart[spanSorted[next].toInt()] <= position) {
                if (activeCount == active.size) active = active.copyOf(activeCount * 2)
                active[activeCount++] = spanSorted[next++].toInt()
            }
            var end = if (next < spanCount) spanStart[spanSorted[next].toInt()] - 1 else Int.MAX_VALUE
            var key = Int.MAX_VALUE
            for (i in 0 until activeCount) {
                val span = active[i]
                if (spanKey[span] < key) key = spanKey[span]
                if (spanEnd[span] < end) end = spanEnd[span]
            }
            if (scatter) {
                val g = gen.toByte()
                val previous = (gen - 1).toByte()
                for (char in position..end) {
                    val seen = stamp[char]
                    if (seen == g || first || seen == previous) hit(char, key, seen)
                }
            } else {
                if (segmentCount == segmentStart.size) {
                    segmentStart = segmentStart.copyOf(segmentCount * 2)
                    segmentEnd = segmentEnd.copyOf(segmentCount * 2)
                    segmentKey = segmentKey.copyOf(segmentCount * 2)
                }
                segmentStart[segmentCount] = position
                segmentEnd[segmentCount] = end
                segmentKey[segmentCount++] = key
            }
            position = end + 1
            var kept = 0
            for (i in 0 until activeCount) if (spanEnd[active[i]] >= position) active[kept++] = active[i]
            activeCount = kept
        }
        if (scatter) return
        val g = gen.toByte()
        val previous = (gen - 1).toByte()
        for (k in 0 until touchedSize) {
            val char = touched[k]
            val seen = stamp[char]
            if (seen != g && seen != previous) continue
            var low = 0
            var high = segmentCount - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (segmentStart[mid] <= char) low = mid + 1 else high = mid - 1
            }
            if (high >= 0 && char <= segmentEnd[high]) hit(char, segmentKey[high], seen)
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
                val quality = if (width == length) WHOLE else PREFIX
                val offset = data.indexOf(data.rangeFirst[range]) - data.rangeFirst[range]
                for (codePoint in from..to) hitGenerated(codePoint + offset, doc, quality, true)
            }
        }
    }

    private fun matchHangulNames(token: Int, start: Int, length: Int) {
        var named = false
        for (other in 0 until tokenCount) {
            if (other != token && (isStartOf(other, HANGUL, 0, HANGUL.size) || isStartOf(other, SYLLABLE, 0, SYLLABLE.size))) named = true
        }
        if (length > HANGUL_NAME_MAX || length < MIN_HANGUL_NAME && !named) return
        var range = 0
        while (range < data.rangeCount && !data.isHangulRange(range)) range++
        if (range == data.rangeCount) return
        val doc = index.rangeDocs[range]
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
                    if (whole || named) hitGenerated(offset + (l * HangulNames.V_COUNT + v) * HangulNames.T_COUNT + t, doc, if (whole) WHOLE else PREFIX, named)
                }
            }
        }
    }

    private fun isStartOf(token: Int, word: ByteArray, start: Int, size: Int): Boolean {
        val length = tokens.length[token]
        if (length > size) return false
        val from = tokens.start[token]
        for (i in 0 until length) if (tokens.bytes[from + i] != word[start + i]) return false
        return true
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
        val SURROGATES = 0xD800..0xDFFF
        const val MAX_LITERALS = 16
        const val MIN_INFIX = 3
        const val MIN_INFIX_CJK = 1
        const val MIN_SPAN = 3
        const val MIN_GENERATED_HEX = 4
        const val MIN_HANGUL_NAME = 2
        const val KANA_FIRST = 0x3040
        const val KANA_LAST = 0x30FF
        const val MIN_SPLIT_FIRST = 3
        const val MIN_SPLIT_SECOND = 2
        const val MAX_SPLIT_LENGTH = 64
        const val HANGUL_NAME_MAX = 7
        const val PROBE_COST = 8L
        const val CODE_TRAILERS = ",.;:)"
        const val WHOLE = 1
        const val PREFIX = 2
        const val INFIX = 3
        const val DOC_BITS = 19
        const val DOC_MASK = (1 shl DOC_BITS) - 1
        const val CHAR_MASK = (1L shl 21) - 1
        const val ORDER_SHIFT = 24
        const val WORDS_MASK = SearchIndex.MAX_WORDS shl DOC_BITS
        const val TIER_SHIFT = 26
        const val TIER_EXACT = 1
        const val TIER_EXACT_ALIAS = 2
        const val TIER_WORD = 3
        const val TIER_WORD_OTHER = 4
        const val TIER_ALIAS_WORD = 5
        const val TIER_START = 6
        const val TIER_START_OTHER = 7
        const val TIER_GENERATED = 8
        const val TIER_ALIAS_START = 9
        const val TIER_KEYWORD = 10
        const val TIER_BLOCK = 11
        const val TIER_SUBHEAD = 12
        const val TIER_INSIDE_NAME = 13
        const val TIER_INSIDE_ALIAS = 14
        const val TIER_INSIDE_KEYWORD = 15
        const val TIER_INSIDE_BLOCK = 16
        const val TIER_SPLIT = 17
        const val TIER_MEANING = 18
        const val MIN_TYPING = 3
        const val TYPING_START = 2
        const val TYPING_WORD = 3
        const val MAX_VERIFIED = 32
        const val MIN_UNFINISHED = 2
        const val LEVEL_COMMON: Byte = 0
        const val LEVEL_KNOWN: Byte = 1
        const val LEVEL_OTHER: Byte = 2
        val COMMON_AREAS = intArrayOf(0x0020, 0x00FF, 0x2000, 0x206F)
        val KNOWN_AREAS = intArrayOf(0x0100, 0x024F, 0x0300, 0x04FF, 0x2000, 0x2BFF, 0x3040, 0x30FF)
        val OTHER_AREAS = intArrayOf(0x007F, 0x009F, 0x2336, 0x237A)
        val HANGUL = "hangul".toByteArray()
        val SYLLABLE = "syllable".toByteArray()
        val HANGUL_L = jamo(HangulNames.L)
        val HANGUL_V = jamo(HangulNames.V)
        val HANGUL_T = jamo(HangulNames.T)

        fun jamo(names: Array<String>) = Array(names.size) { names[it].lowercase(Locale.ROOT).toByteArray() }
    }
}
