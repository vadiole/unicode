package vadiole.unicode.search

import vadiole.unicode.data.TaggedTexts
import vadiole.unicode.data.TextTable
import vadiole.unicode.data.UnicodeData

class UnicodeDataSource(private val data: UnicodeData, private val meanings: () -> TaggedTexts) : SearchSource {
    override val key = "en"

    override fun emit(builder: SearchIndexBuilder) {
        val names = data.names
        var nameIndex = 0
        var range = 0
        var char = 0
        while (char < data.size) {
            val codePoint = data.codePointAt(char)
            if (range < data.rangeCount && data.rangeFirst[range] == codePoint) {
                val last = char + data.rangeLast[range] - codePoint
                builder.addRangeName(char, last, data.rangePrefixes[range].toByteArray())
                range++
                char = last + 1
            } else {
                builder.add(SearchKind.NAME, 0, char++, names.bytes, names.start(nameIndex), names.end(nameIndex))
                nameIndex++
            }
        }
        val secondNames = data.secondNames
        var texts = secondNames.texts
        for (i in 0 until secondNames.size) {
            builder.add(secondKinds[secondNames.tags[i].toInt()], 0, data.indexOf(secondNames.codePoints[i]), texts.bytes, texts.start(i), texts.end(i))
        }
        val keywords = data.keywords
        texts = keywords.texts
        for (i in 0 until keywords.size) {
            val codePoint = keywords.codePoints[i]
            val name = data.nameIndex(codePoint)
            val start = texts.start(i)
            val end = if (name >= 0 && isWord(names, name, texts.bytes, start, texts.end(i))) start else texts.end(i)
            builder.add(SearchKind.KEYWORD, 0, data.indexOf(codePoint), texts.bytes, start, end)
        }
        texts = data.blockNames
        for (i in 0 until texts.size) {
            val first = data.lowerIndex(data.blockStart[i])
            val last = data.lowerIndex(data.blockEnd[i] + 1) - 1
            if (last >= first) builder.add(SearchKind.BLOCK, 0, first, last, texts.bytes, texts.start(i), texts.end(i), 0)
        }
        texts = data.subheads
        for (i in 0 until texts.size) {
            builder.add(SearchKind.SUBHEAD, 0, data.indexOf(data.subheadStart[i]), data.indexOf(data.subheadEnd[i]), texts.bytes, texts.start(i), texts.end(i), 0)
        }
        val meanings = meanings()
        texts = meanings.texts
        val bytes = texts.bytes
        for (i in 0 until meanings.size) {
            val index = data.indexOf(meanings.codePoints[i])
            val level = meanings.tags[i].toInt()
            var start = texts.start(i)
            val end = texts.end(i)
            for (at in start..end) {
                if (at == end || bytes[at] == SEMICOLON || bytes[at] == COMMA) {
                    if (at > start) builder.add(SearchKind.MEANING, level, index, bytes, start, at)
                    start = at + 1
                }
            }
        }
    }

    override fun text(index: SearchIndex, doc: Int, codePoint: Int): String {
        return when (index.kind(doc)) {
            SearchKind.NAME -> data.name(codePoint).orEmpty()
            SearchKind.KEYWORD -> data.keywords.texts[doc - firstDoc(index, doc) + data.keywords.first(codePoint)]
            SearchKind.BLOCK -> data.blockName(codePoint).orEmpty()
            SearchKind.SUBHEAD -> data.subhead(codePoint).orEmpty()
            SearchKind.MEANING -> {
                val meanings = meanings()
                val text = meanings.texts[meanings.first(codePoint)]
                var skip = doc - firstDoc(index, doc)
                forEachSense(text) { sense -> if (skip-- == 0) return sense.toString().trim() }
                text
            }
            else -> data.secondNames.texts[doc - firstDoc(index, doc) + data.secondNames.first(codePoint)]
        }
    }

    private fun isWord(names: TextTable, name: Int, word: ByteArray, from: Int, to: Int): Boolean {
        val bytes = names.bytes
        val length = to - from
        val end = names.end(name)
        var start = names.start(name)
        for (at in start..end) {
            if (at < end && isLetter(bytes[at])) continue
            if (at - start == length) {
                var i = 0
                while (i < length && bytes[start + i].toInt() or 0x20 == word[from + i].toInt() or 0x20 && isLetter(word[from + i])) i++
                if (i == length) return true
            }
            start = at + 1
        }
        return false
    }

    private fun isLetter(b: Byte) = b.toInt() or 0x20 in 'a'.code..'z'.code

    private inline fun forEachSense(text: String, action: (CharSequence) -> Unit) {
        var start = 0
        for (end in 0..text.length) {
            if (end == text.length || text[end] == ';' || text[end] == ',') {
                if (end > start) action(text.subSequence(start, end))
                start = end + 1
            }
        }
    }

    private fun firstDoc(index: SearchIndex, doc: Int): Int {
        val char = index.docChar[doc]
        val section = sections[index.kind(doc)]
        var first = doc
        while (first > 0 && index.docChar[first - 1] == char && sections[index.kind(first - 1)] == section) first--
        return first
    }

    private companion object {
        const val SEMICOLON = ';'.code.toByte()
        const val COMMA = ','.code.toByte()
        val sections = intArrayOf(0, 1, 1, 1, 1, 1, 2, 3, 4, 5)
        val secondKinds = intArrayOf(
            SearchKind.UNICODE_1, SearchKind.FORMAL_ALIAS, SearchKind.FORMAL_ALIAS, SearchKind.FORMAL_ALIAS,
            SearchKind.FORMAL_ALIAS, SearchKind.ABBREVIATION, SearchKind.ALIAS, SearchKind.SHORT_NAME,
        )
    }
}
