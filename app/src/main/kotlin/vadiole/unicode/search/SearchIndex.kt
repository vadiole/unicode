package vadiole.unicode.search

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

object SearchKind {
    const val NAME = 0
    const val UNICODE_1 = 1
    const val FORMAL_ALIAS = 2
    const val ABBREVIATION = 3
    const val ALIAS = 4
    const val SHORT_NAME = 5
    const val KEYWORD = 6
    const val BLOCK = 7
    const val SUBHEAD = 8
    const val MEANING = 9
}

interface SearchSource {
    val key: String
    fun emit(builder: SearchIndexBuilder)
    fun text(index: SearchIndex, doc: Int, codePoint: Int): String
}

class SearchIndex(
    val wordBytes: ByteArray,
    val wordOff: IntArray,
    val postBytes: ByteArray,
    val postOff: IntArray,
    val docChar: IntArray,
    val docMeta: ByteArray,
    val docWords: ByteArray,
    val spanDocs: IntArray,
    val spanLast: IntArray,
    val rangeDocs: IntArray,
    val sourceStart: IntArray,
) {
    val words: Int get() = wordOff.size - 1
    val docs: Int get() = docChar.size
    val bytes: Long
        get() = wordBytes.size + postBytes.size + docMeta.size + docWords.size +
                4L * (wordOff.size + postOff.size + docChar.size + spanDocs.size + spanLast.size + rangeDocs.size + sourceStart.size)

    fun kind(doc: Int): Int = docMeta[doc].toInt() and KIND_MASK

    fun sourceOf(doc: Int): Int {
        var source = 0
        while (source + 1 < sourceStart.size && sourceStart[source + 1] <= doc) source++
        return source
    }

    fun spanLast(doc: Int): Int = spanLast[java.util.Arrays.binarySearch(spanDocs, doc)]

    fun save(output: OutputStream, key: String) {
        val checked = CheckedOutputStream(output, CRC32())
        val out = DataOutputStream(checked)
        out.writeInt(MAGIC)
        out.writeUTF(key)
        val ints = arrayOf(wordOff, postOff, docChar, spanDocs, spanLast, rangeDocs, sourceStart)
        val blobs = arrayOf(wordBytes, postBytes, docMeta, docWords)
        ints.forEach { out.writeInt(it.size) }
        blobs.forEach { out.writeInt(it.size) }
        for (values in ints) {
            val buffer = ByteBuffer.allocate(values.size * 4)
            buffer.asIntBuffer().put(values)
            out.write(buffer.array())
        }
        blobs.forEach { out.write(it) }
        out.writeLong(checked.checksum.value)
        out.flush()
    }

    companion object {
        const val KIND_MASK = 15
        const val LEVEL_SHIFT = 4
        const val SHORT = 1 shl 6
        const val SPAN = 1 shl 7
        const val MAX_DOCS = 1 shl 19
        const val MAX_WORDS = 63
        const val POSITION_BITS = 2
        const val POSITION_MASK = 3
        const val FIRST_WORD = 1
        const val LAST_WORD = 2
        private const val MAGIC = 0x55534931
        private const val INT_ARRAYS = 7
        private const val BLOBS = 4

        fun load(input: InputStream, key: String, length: Long): SearchIndex? {
            val checked = CheckedInputStream(input, CRC32())
            val data = DataInputStream(checked)
            if (data.readInt() != MAGIC || data.readUTF() != key) return null
            val sizes = IntArray(INT_ARRAYS + BLOBS) { data.readInt() }
            var expected = 0L
            for (i in sizes.indices) {
                if (sizes[i] < 0) return null
                expected += if (i < INT_ARRAYS) 4L * sizes[i] else sizes[i].toLong()
            }
            if (expected > length) return null
            val ints = Array(INT_ARRAYS) { i ->
                val bytes = ByteArray(sizes[i] * 4).also(data::readFully)
                IntArray(sizes[i]).also { ByteBuffer.wrap(bytes).asIntBuffer().get(it) }
            }
            val blobs = Array(BLOBS) { i -> ByteArray(sizes[INT_ARRAYS + i]).also(data::readFully) }
            val checksum = checked.checksum.value
            if (data.readLong() != checksum) throw IOException("Search index checksum mismatch")
            return SearchIndex(blobs[0], ints[0], blobs[1], ints[1], ints[2], blobs[2], blobs[3], ints[3], ints[4], ints[5], ints[6])
        }
    }
}

class SearchIndexBuilder {
    private companion object {
        val GENERIC_WORDS = arrayOf("sign".toByteArray(), "mark".toByteArray(), "symbol".toByteArray())
    }

    private val tokens = SearchTokens(Int.MAX_VALUE, symbols = false)
    private val symbolTokens = SearchTokens(Int.MAX_VALUE, symbols = true)
    private var pool = ByteArray(1 shl 18)
    private var poolSize = 0
    private var table = IntArray(1 shl 16) { -1 }
    private var wordStart = IntArray(1 shl 14)
    private var wordLength = IntArray(1 shl 14)
    private var wordHash = IntArray(1 shl 14)
    private var wordCount = 0
    private var occWord = IntArray(1 shl 18)
    private var occDoc = IntArray(1 shl 18)
    private var occCount = 0
    private var docChar = IntArray(1 shl 16)
    private var docMeta = ByteArray(1 shl 16)
    private var docWords = ByteArray(1 shl 16)
    private var docCount = 0
    private var spanDocs = IntArray(1 shl 10)
    private var spanLast = IntArray(1 shl 10)
    private var spanCount = 0
    private var rangeDocs = IntArray(16)
    private var rangeCount = 0
    private var sourceStart = IntArray(4)
    private var sourceCount = 0

    fun beginSource() {
        if (sourceCount + 1 >= sourceStart.size) sourceStart = sourceStart.copyOf(sourceStart.size * 2)
        sourceStart[sourceCount++] = docCount
    }

    fun add(kind: Int, level: Int, char: Int, text: CharSequence) = add(kind, level, char, char, text, 0)

    fun addRangeName(first: Int, last: Int, prefix: CharSequence) {
        if (rangeCount == rangeDocs.size) rangeDocs = rangeDocs.copyOf(rangeCount * 2)
        rangeDocs[rangeCount++] = add(SearchKind.NAME, 0, first, last, prefix, 1)
    }

    fun add(kind: Int, level: Int, first: Int, last: Int, text: CharSequence, extraWords: Int): Int {
        val tokens = if (kind == SearchKind.KEYWORD) symbolTokens else tokens
        tokens.set(text)
        if (docCount == docChar.size) {
            docChar = docChar.copyOf(docCount * 2)
            docMeta = docMeta.copyOf(docCount * 2)
            docWords = docWords.copyOf(docCount * 2)
        }
        val doc = docCount++
        check(doc < SearchIndex.MAX_DOCS) { "Too many search documents" }
        var meta = kind or (level shl SearchIndex.LEVEL_SHIFT)
        if (kind != SearchKind.NAME && tokens.count == 1 && tokens.length[0] <= 2) meta = meta or SearchIndex.SHORT
        if (last > first) {
            meta = meta or SearchIndex.SPAN
            if (spanCount == spanDocs.size) {
                spanDocs = spanDocs.copyOf(spanCount * 2)
                spanLast = spanLast.copyOf(spanCount * 2)
            }
            spanDocs[spanCount] = doc
            spanLast[spanCount++] = last
        }
        docChar[doc] = first
        docMeta[doc] = meta.toByte()
        docWords[doc] = minOf(tokens.count + extraWords, SearchIndex.MAX_WORDS).toByte()
        var last = tokens.count - 1
        if (kind < SearchKind.KEYWORD) {
            while (last > 0 && isGeneric(tokens, last)) last--
        }
        for (i in 0 until tokens.count) {
            if (occCount == occWord.size) {
                occWord = occWord.copyOf(occCount * 2)
                occDoc = occDoc.copyOf(occCount * 2)
            }
            var position = if (i == 0) SearchIndex.FIRST_WORD else 0
            if (i == last && extraWords == 0) position = position or SearchIndex.LAST_WORD
            occWord[occCount] = wordId(tokens.bytes, tokens.start[i], tokens.length[i])
            occDoc[occCount++] = (doc shl SearchIndex.POSITION_BITS) or position
        }
        return doc
    }

    private fun isGeneric(tokens: SearchTokens, index: Int): Boolean {
        for (word in GENERIC_WORDS) {
            if (word.size != tokens.length[index]) continue
            var i = 0
            while (i < word.size && word[i] == tokens.bytes[tokens.start[index] + i]) i++
            if (i == word.size) return true
        }
        return false
    }

    private fun wordId(bytes: ByteArray, start: Int, length: Int): Int {
        var hash = -2128831035
        for (i in start until start + length) hash = (hash xor (bytes[i].toInt() and 0xFF)) * 16777619
        var slot = (hash xor (hash ushr 15)) and (table.size - 1)
        while (true) {
            val id = table[slot]
            if (id < 0) break
            if (wordHash[id] == hash && wordLength[id] == length) {
                val from = wordStart[id]
                var i = 0
                while (i < length && pool[from + i] == bytes[start + i]) i++
                if (i == length) return id
            }
            slot = (slot + 1) and (table.size - 1)
        }
        if (wordCount == wordStart.size) {
            wordStart = wordStart.copyOf(wordCount * 2)
            wordLength = wordLength.copyOf(wordCount * 2)
            wordHash = wordHash.copyOf(wordCount * 2)
        }
        if (poolSize + length > pool.size) pool = pool.copyOf(maxOf(pool.size * 2, poolSize + length))
        System.arraycopy(bytes, start, pool, poolSize, length)
        val id = wordCount++
        wordStart[id] = poolSize
        wordLength[id] = length
        wordHash[id] = hash
        poolSize += length
        table[slot] = id
        if (wordCount * 2 > table.size) {
            table = IntArray(table.size * 2) { -1 }
            for (word in 0 until wordCount) {
                val h = wordHash[word]
                var s = (h xor (h ushr 15)) and (table.size - 1)
                while (table[s] >= 0) s = (s + 1) and (table.size - 1)
                table[s] = word
            }
        }
        return id
    }

    fun build(): SearchIndex {
        val order = IntArray(wordCount) { it }
        sort(order, 0, wordCount - 1)
        val rank = IntArray(wordCount)
        for (r in 0 until wordCount) rank[order[r]] = r
        val counts = IntArray(wordCount + 1)
        for (o in 0 until occCount) counts[rank[occWord[o]] + 1]++
        for (r in 0 until wordCount) counts[r + 1] += counts[r]
        val position = counts.copyOf()
        val sortedDocs = IntArray(occCount)
        for (o in 0 until occCount) sortedDocs[position[rank[occWord[o]]]++] = occDoc[o]
        val post = ByteArray(occCount * 4)
        val postOff = IntArray(wordCount + 1)
        val wordBytes = ByteArray(poolSize + wordCount)
        val wordOff = IntArray(wordCount + 1)
        var postSize = 0
        var wordSize = 0
        for (r in 0 until wordCount) {
            postOff[r] = postSize
            var previous = -1
            for (k in counts[r] until counts[r + 1]) {
                val doc = sortedDocs[k] ushr SearchIndex.POSITION_BITS
                var delta = ((doc - previous) shl SearchIndex.POSITION_BITS) or (sortedDocs[k] and SearchIndex.POSITION_MASK)
                previous = doc
                while (delta >= 0x80) {
                    post[postSize++] = (delta or 0x80).toByte()
                    delta = delta ushr 7
                }
                post[postSize++] = delta.toByte()
            }
            val id = order[r]
            wordOff[r] = wordSize
            System.arraycopy(pool, wordStart[id], wordBytes, wordSize, wordLength[id])
            wordSize += wordLength[id]
            wordBytes[wordSize++] = '\n'.code.toByte()
        }
        postOff[wordCount] = postSize
        wordOff[wordCount] = wordSize
        sourceStart[sourceCount] = docCount
        return SearchIndex(
            wordBytes, wordOff, post.copyOf(postSize), postOff,
            docChar.copyOf(docCount), docMeta.copyOf(docCount), docWords.copyOf(docCount),
            spanDocs.copyOf(spanCount), spanLast.copyOf(spanCount), rangeDocs.copyOf(rangeCount), sourceStart.copyOf(sourceCount + 1),
        )
    }

    private fun compare(a: Int, b: Int): Int {
        val lengthA = wordLength[a]
        val lengthB = wordLength[b]
        val startA = wordStart[a]
        val startB = wordStart[b]
        for (i in 0 until minOf(lengthA, lengthB)) {
            val x = pool[startA + i].toInt() and 0xFF
            val y = pool[startB + i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return lengthA - lengthB
    }

    private fun sort(a: IntArray, from: Int, to: Int) {
        var low = from
        var high = to
        while (low < high) {
            val pivot = a[(low + high) ushr 1]
            var i = low
            var j = high
            while (i <= j) {
                while (compare(a[i], pivot) < 0) i++
                while (compare(a[j], pivot) > 0) j--
                if (i <= j) {
                    val t = a[i]
                    a[i] = a[j]
                    a[j] = t
                    i++
                    j--
                }
            }
            if (j - low < high - i) {
                sort(a, low, j)
                low = i
            } else {
                sort(a, i, high)
                high = j
            }
        }
    }
}
