package vadiole.unicode.search

import vadiole.unicode.data.Annotations
import vadiole.unicode.data.UnicodeData

class AnnotationSource(private val data: UnicodeData, private val annotations: Annotations) : SearchSource {
    override val key = annotations.key

    override fun emit(builder: SearchIndexBuilder) {
        val bytes = annotations.bytes
        for (entry in 0 until annotations.size) {
            val char = data.indexOf(annotations.codePoints[entry])
            if (char < 0) continue
            forEachText(entry) { kind, start, end -> builder.add(kind, 0, char, bytes, start, end) }
        }
    }

    override fun text(index: SearchIndex, doc: Int, codePoint: Int): String {
        val entry = annotations.codePoints.binarySearch(codePoint)
        if (entry < 0) return ""
        val char = index.docChar[doc]
        val first = index.sourceStart[index.sourceOf(doc)]
        var skip = 0
        while (doc - skip > first && index.docChar[doc - skip - 1] == char) skip++
        forEachText(entry) { _, start, end -> if (skip-- == 0) return annotations.text(start, end) }
        return ""
    }

    private inline fun forEachText(entry: Int, action: (kind: Int, start: Int, end: Int) -> Unit) {
        val bytes = annotations.bytes
        var start = annotations.start(entry)
        var end = annotations.end(start)
        if (end > start) action(SearchKind.SHORT_NAME, start, end)
        repeat(KEYWORD_FIELDS) {
            start = end + 1
            end = annotations.end(start)
            for (at in start..end) {
                if (at == end || bytes[at] == SEPARATOR) {
                    if (at > start) action(SearchKind.KEYWORD, start, at)
                    start = at + 1
                }
            }
        }
    }

    private companion object {
        const val KEYWORD_FIELDS = 2
        const val SEPARATOR = Annotations.SEPARATOR.code.toByte()
    }
}
