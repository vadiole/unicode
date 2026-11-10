package vadiole.unicode.search

import java.io.File
import vadiole.unicode.data.TaggedTexts
import vadiole.unicode.data.UnicodeData

object TestData {
    private val asset = File("src/main/assets/u18.bin")
    val data: UnicodeData = asset.inputStream().buffered().use { UnicodeData.read(it) }
    val meanings: TaggedTexts = asset.inputStream().buffered().use { UnicodeData.readMeanings(it) }
    val sources = arrayOf<SearchSource>(UnicodeDataSource(data) { meanings })
    val index: SearchIndex = SearchIndexBuilder().also { builder ->
        for (source in sources) {
            builder.beginSource()
            source.emit(builder)
        }
    }.build()
    val search = CharSearch(data, index, sources)
}
