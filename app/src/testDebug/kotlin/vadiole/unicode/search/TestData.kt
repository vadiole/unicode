package vadiole.unicode.search

import java.io.File
import vadiole.unicode.data.Annotations
import vadiole.unicode.data.TaggedTexts
import vadiole.unicode.data.UnicodeData
import vadiole.unicode.data.UnicodeDataReader

object TestData {
    private val asset = File("src/main/assets/u18.bin")
    private val reader = UnicodeDataReader(asset.inputStream().buffered())
    val data: UnicodeData = reader.data()
    val meanings: TaggedTexts = reader.meanings()
    val sources = arrayOf<SearchSource>(UnicodeDataSource(data) { meanings })
    val index: SearchIndex = SearchIndex.build(sources)
    val search = CharSearch(data, index, sources)

    fun annotations(qualifier: String): Annotations {
        return File("src/main/res/raw-$qualifier/annotations.txt").inputStream().buffered().use { Annotations.read(requireNotNull(Annotations.readKey(it)), it) }
    }

    fun localized(qualifier: String): CharSearch {
        val sources = arrayOf(sources[0], AnnotationSource(data, annotations(qualifier)))
        return CharSearch(data, SearchIndex.build(sources), sources)
    }
}
