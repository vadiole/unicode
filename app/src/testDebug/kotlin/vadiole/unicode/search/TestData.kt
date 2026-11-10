package vadiole.unicode.search

import java.io.File
import vadiole.unicode.data.TaggedTexts
import vadiole.unicode.data.UnicodeData
import vadiole.unicode.data.UnicodeDataReader

object TestData {
    private val asset = File("src/main/assets/u18.bin")
    private val reader = UnicodeDataReader(asset.inputStream().buffered())
    val data: UnicodeData = reader.data()
    val meanings: TaggedTexts = reader.meanings()
    val sources = arrayOf<SearchSource>(UnicodeDataSource(data) { meanings })
    val index: SearchIndex = SearchIndexBuilder().also { builder ->
        for (source in sources) {
            builder.beginSource()
            source.emit(builder)
        }
    }.build()
    val search = CharSearch(data, index, sources)
}
