package vadiole.unicode.search

import vadiole.unicode.data.TaggedTexts
import vadiole.unicode.data.UnicodeData

class UnicodeDataSource(private val data: UnicodeData, private val meanings: () -> TaggedTexts) : SearchSource {
    override val key = "en"

    override fun emit(builder: SearchIndexBuilder) {
        var nameIndex = 0
        var range = 0
        var char = 0
        while (char < data.size) {
            val codePoint = data.codePointAt(char)
            if (range < data.rangeCount && data.rangeFirst[range] == codePoint) {
                val last = char + data.rangeLast[range] - codePoint
                builder.addRangeName(char, last, data.rangePrefixes[range])
                range++
                char = last + 1
            } else {
                builder.add(SearchKind.NAME, 0, char++, data.names[nameIndex++])
            }
        }
        val secondNames = data.secondNames
        for (i in 0 until secondNames.size) {
            builder.add(secondKinds[secondNames.tags[i].toInt()], 0, data.indexOf(secondNames.codePoints[i]), secondNames.texts[i])
        }
        val keywords = data.keywords
        for (i in 0 until keywords.size) {
            builder.add(SearchKind.KEYWORD, 0, data.indexOf(keywords.codePoints[i]), keywords.texts[i])
        }
        for (i in 0 until data.blockNames.size) {
            val first = data.lowerIndex(data.blockStart[i])
            val last = data.lowerIndex(data.blockEnd[i] + 1) - 1
            if (last >= first) builder.add(SearchKind.BLOCK, 0, first, last, data.blockNames[i], 0)
        }
        for (i in 0 until data.subheads.size) {
            builder.add(SearchKind.SUBHEAD, 0, data.indexOf(data.subheadStart[i]), data.indexOf(data.subheadEnd[i]), data.subheads[i], 0)
        }
        val meanings = meanings()
        for (i in 0 until meanings.size) {
            val index = data.indexOf(meanings.codePoints[i])
            val level = meanings.tags[i].toInt()
            forEachSense(meanings.texts[i]) { sense -> builder.add(SearchKind.MEANING, level, index, sense) }
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
        val sections = intArrayOf(0, 1, 1, 1, 1, 1, 2, 3, 4, 5)
        val secondKinds = intArrayOf(
            SearchKind.UNICODE_1, SearchKind.FORMAL_ALIAS, SearchKind.FORMAL_ALIAS, SearchKind.FORMAL_ALIAS,
            SearchKind.FORMAL_ALIAS, SearchKind.ABBREVIATION, SearchKind.ALIAS, SearchKind.SHORT_NAME,
        )
    }
}
