import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vadiole.unicode.data.UnicodeData
import vadiole.unicode.search.CharSearch
import vadiole.unicode.search.SearchIndex
import vadiole.unicode.search.SearchIndexBuilder
import vadiole.unicode.search.SearchSource
import vadiole.unicode.search.UnicodeDataSource

class GeneratorTest {
    private fun displayed(keywords: String, formalName: String, shortName: String): String {
        return orderKeywords(keywords.split(" | "), formalName, shortName).filter { it.second }.joinToString(" | ") { it.first }
    }

    @Test
    fun keywordsCoveredByTheNamesAreSearchOnly() {
        assertEquals("good | hand | like | yes | +1", displayed("+1 | good | hand | like | thumb | up | yes", "THUMBS UP SIGN", "thumbs up"))
        assertEquals("emotion | love", displayed("emotion | heart | love | red", "HEAVY BLACK HEART", "red heart"))
        assertEquals("laugh", displayed("face | faces | grinning | grinned | laugh", "GRINNING FACE", "grinning face"))
        assertEquals("", displayed("smiley | smilng", "WHITE SMILING FACE", "smiling face"))
    }

    @Test
    fun digitsAndSingleSymbolsAreSearchOnly() {
        assertEquals("ily | <3 | 4:30 | ...", displayed("143 | 3 | ! | ✓ | <3 | ily | 4:30 | ...", "SMILING FACE WITH HEARTS", "smiling face with hearts"))
    }

    @Test
    fun aliasLinesAreSplitOutsideParentheses() {
        assertEquals(listOf("period", "dot", "decimal point"), splitAliases("period, dot, decimal point"))
        assertEquals(listOf("derivative (Newtonian notation, old)"), splitAliases("derivative (Newtonian notation, old)"))
        assertEquals(listOf("latin capital ligature ae"), splitAliases("latin capital ligature ae (1.1)"))
    }

    private val sample = GeneratorData(
        codePoints = intArrayOf(0x41, 0x42, 0xAD, 0x4E00, 0x4E01, 0x4E02, 0xAC00, 0xAC01, 0x1F600),
        kinds = intArrayOf(1, 1, 2, 1, 1, 1, 1, 1, 1),
        versions = arrayOf("1.1", "1.1", "1.1", "1.1", "1.1", "13.0", "2.0", "2.0", "6.1"),
        blocks = listOf(GeneratorBlock(0, 0x7F, "Basic Latin"), GeneratorBlock(0x80, 0xFF, "Latin-1 Supplement"), GeneratorBlock(0x4E00, 0x9FFF, "CJK Unified Ideographs"), GeneratorBlock(0xAC00, 0xD7AF, "Hangul Syllables"), GeneratorBlock(0x1F600, 0x1F64F, "Emoticons")),
        subheads = listOf(GeneratorBlock(0x41, 0x42, "Uppercase Latin alphabet"), GeneratorBlock(0x1F600, 0x1F600, "Faces")),
        ranges = listOf(GeneratorRange(0x4E00, 0x4E02, RANGE_HEX, "CJK UNIFIED IDEOGRAPH"), GeneratorRange(0xAC00, 0xAC01, RANGE_HANGUL, "HANGUL SYLLABLE")),
        names = listOf("LATIN CAPITAL LETTER A", "LATIN CAPITAL LETTER B", "SOFT HYPHEN", "GRINNING FACE"),
        secondNames = listOf(GeneratorText(0xAD, SECOND_ABBREVIATION, "SHY"), GeneratorText(0xAD, SECOND_ALIAS, "discretionary hyphen")),
        keywords = listOf(GeneratorText(0x1F600, KEYWORD_DISPLAY, "cheerful"), GeneratorText(0x1F600, 0, "face")),
        meanings = listOf(GeneratorText(0x4E00, HAN_SCHOOL, "one; a, an; alone")),
    )

    @Test
    fun fileRoundTrip() {
        val bytes = encodeFile(encodeSections(sample))
        val data = UnicodeData.read(ByteArrayInputStream(bytes))
        val meanings = UnicodeData.readMeanings(ByteArrayInputStream(bytes))
        assertEquals(DATA_VERSION, data.version)
        assertArrayEquals(sample.codePoints, data.codePoints(data.size))
        assertEquals(-1, data.indexOf(0x43))
        assertEquals(UnicodeData.KIND_ABBREVIATION, data.kindAt(data.indexOf(0xAD)))
        assertEquals("13.0", data.versionOf(0x4E02))
        assertEquals("LATIN CAPITAL LETTER B", data.name(0x42))
        assertEquals("GRINNING FACE", data.name(0x1F600))
        assertEquals("CJK UNIFIED IDEOGRAPH-4E01", data.name(0x4E01))
        assertEquals("HANGUL SYLLABLE GAG", data.name(0xAC01))
        assertNull(data.name(0xE000))
        assertEquals("Latin-1 Supplement", data.blockName(0xAD))
        assertNull(data.blockName(0x100))
        assertEquals("Uppercase Latin alphabet", data.subhead(0x42))
        assertNull(data.subhead(0xAD))
        assertEquals(listOf("5:SHY", "6:discretionary hyphen"), data.secondNames(0xAD).map { "${it.kind}:${it.text}" })
        assertEquals(listOf("cheerful"), data.displayKeywords(0x1F600).toList())
        assertEquals("one; a, an; alone", meanings.texts[meanings.first(0x4E00)])
        assertEquals(UnicodeData.HAN_SCHOOL, meanings.tags[0].toInt())
    }

    @Test
    fun indexCacheRoundTrip() {
        val bytes = encodeFile(encodeSections(sample))
        val data = UnicodeData.read(ByteArrayInputStream(bytes))
        val meanings = UnicodeData.readMeanings(ByteArrayInputStream(bytes))
        val sources = arrayOf<SearchSource>(UnicodeDataSource(data) { meanings })
        val builder = SearchIndexBuilder()
        builder.beginSource()
        sources[0].emit(builder)
        val saved = ByteArrayOutputStream().also { builder.build().save(it, "key") }.toByteArray()
        val index = requireNotNull(SearchIndex.load(ByteArrayInputStream(saved), "key", saved.size.toLong()))
        val search = CharSearch(data, index, sources)
        assertArrayEquals(intArrayOf(0xAD), search.search("shy", 0).codePoints)
        assertArrayEquals(intArrayOf(0x4E00), search.search("alone", 0).codePoints)
        assertArrayEquals(intArrayOf(0x4E01), search.search("cjk 4e01", 0).codePoints)
        assertNull(SearchIndex.load(ByteArrayInputStream(saved), "other", saved.size.toLong()))
        for (position in intArrayOf(8, 20, saved.size / 2, saved.size - 1)) {
            val corrupt = saved.copyOf()
            corrupt[position] = (corrupt[position] + 1).toByte()
            val loaded = try {
                SearchIndex.load(ByteArrayInputStream(corrupt), "key", corrupt.size.toLong())
            } catch (e: Exception) {
                null
            }
            assertNull("corrupt byte $position", loaded)
        }
        val truncated = try {
            SearchIndex.load(ByteArrayInputStream(saved, 0, saved.size / 2), "key", saved.size / 2L)
        } catch (e: Exception) {
            null
        }
        assertNull(truncated)
    }
}
