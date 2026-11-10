package vadiole.unicode.search

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vadiole.unicode.data.Annotations

class LocalizedSearchTest {
    private val german by lazy { TestData.localized("de") }
    private val ukrainian by lazy { TestData.localized("uk") }
    private val japanese by lazy { TestData.localized("ja") }
    private val chinese by lazy { TestData.localized("zh-rCN") }

    private fun assertFinds(search: CharSearch, query: String, codePoint: Int, top: Int = 64) {
        val found = search.search(query, top).codePoints
        assertTrue("$query: ${found.joinToString(" ") { SearchSuite.hex(it) }}", codePoint in found)
    }

    private fun words(text: String): List<String> {
        val tokens = SearchTokens(Int.MAX_VALUE, distinct = false)
        tokens.set(text)
        return List(tokens.count) { tokens.token(it) }
    }

    @Test
    fun heartInEveryLanguage() {
        assertFinds(TestData.search, "heart", 0x2764, 10)
        assertFinds(german, "herz", 0x2764, 10)
        assertFinds(german, "Herz", 0x2665, 10)
        assertFinds(ukrainian, "серце", 0x2764, 10)
        assertFinds(ukrainian, "СЕРЦЕ", 0x2764, 10)
        assertFinds(japanese, "ハート", 0x2665, 1)
        assertFinds(japanese, "ハート", 0x2764)
        assertFinds(japanese, "はーと", 0x2764)
        assertFinds(japanese, "ﾊｰﾄ", 0x2764)
        assertFinds(chinese, "心", 0x2764)
        assertFinds(chinese, "红心", 0x2764, 3)
    }

    @Test
    fun sharpSMatchesDoubleS() {
        val sharp = german.search("straße", 0).codePoints
        assertArrayEquals(sharp, german.search("strasse", 0).codePoints)
        assertArrayEquals(sharp, german.search("STRASSE", 0).codePoints)
        assertTrue(0x1F68A in sharp && 0x1F30C in sharp && 0x26D4 in sharp)
    }

    @Test
    fun wordsMatchInsideWords() {
        assertFinds(german, "bahn", 0x1F68A)
        assertFinds(german, "strassenb", 0x1F68A, 3)
        assertFinds(japanese, "ート", 0x2764)
        assertFinds(japanese, "愛", 0x2764)
        assertFinds(chinese, "爱", 0x2764)
        assertTrue(0x2764 !in ukrainian.search("рц", 0).codePoints)
        assertFinds(ukrainian, "ерц", 0x2764)
    }

    @Test
    fun englishAndLocalizedWordsMix() {
        assertFinds(german, "red herz", 0x2764, 5)
        assertFinds(ukrainian, "heart червоне", 0x2764, 5)
        assertFinds(japanese, "heart 赤", 0x2764, 5)
        assertFinds(german, "blaues heart", 0x1F499, 1)
    }

    @Test
    fun matchTextIsLocalized() {
        val results = german.search("herz", 10)
        val position = results.codePoints.indexOf(0x2764)
        assertEquals("Herz", results.match(position)?.text)
        val name = german.search("rotes", 10)
        assertEquals("rotes Herz", name.match(name.codePoints.indexOf(0x2764))?.text)
        assertEquals(SearchKind.SHORT_NAME, name.kind(name.codePoints.indexOf(0x2764)))
        val keyword = japanese.search("愛", 10)
        assertEquals("愛", keyword.match(keyword.codePoints.indexOf(0x2764))?.text)
        assertEquals(SearchKind.KEYWORD, keyword.kind(keyword.codePoints.indexOf(0x2764)))
    }

    @Test
    fun englishResultsStayWithALocalizedSource() {
        val files = SuiteFiles.load { name -> requireNotNull(javaClass.getResource("/search/$name")).readText() }
        val english = TestData.search
        val queries = files.queries.map { it.text }.distinct().filter { query ->
            val words = words(query)
            query.all { it.code < 0x80 } && words.isNotEmpty() && words.all { word -> word.length > 1 && word.any { it.isLetter() } }
        }
        assertTrue("${queries.size}", queries.size > 800)
        for ((language, search) in listOf("de" to german, "uk" to ukrainian, "ja" to japanese, "zh-CN" to chinese)) {
            var same = 0
            var sameFirst = 0
            for (query in queries) {
                val expected = english.search(query, SearchSuite.FIRST_LIMIT)
                val actual = search.search(query, SearchSuite.FIRST_LIMIT)
                assertTrue("$language $query", actual.total >= expected.total)
                if (expected.codePoints.firstOrNull() == actual.codePoints.firstOrNull()) sameFirst++
                if (expected.total == actual.total && expected.codePoints.contentEquals(actual.codePoints)) same++
            }
            println("$language: $same of ${queries.size} english queries unchanged, first result of $sameFirst")
            assertTrue("$language first $sameFirst of ${queries.size}", sameFirst >= queries.size * 99 / 100)
            assertTrue("$language $same of ${queries.size}", same >= queries.size * (if (language == "de") 80 else 98) / 100)
            val engine = SearchEngine { query, limit -> search.search(query, limit).let { SearchHits(it.codePoints, it.total) } }
            val failed = files.expectations.filter { SearchSuite.check(it, engine.search(it.query, 0).codePoints) != null }
            assertTrue(failed.joinToString("\n") { language + " " + it.source }, failed.isEmpty())
        }
    }

    @Test
    fun folding() {
        assertEquals(listOf("strasse", "ss"), words("Straße ẞ"))
        assertEquals(listOf("cafe", "naive", "zloty", "o", "coeur", "aeble", "isik"), words("Café naïve złoty Ø cœur Æble ışık"))
        assertEquals(listOf("й", "йод", "ёж"), words("Й йод Ёж"))
        assertEquals(words("йод"), words("йод"))
        assertEquals(listOf("ぶ", "ふ", "ぱ"), words("ブ フ パ"))
        assertEquals(words("ぶどう"), words("ブドウ"))
        assertEquals(words("ブドウ"), words("ﾌﾞﾄﾞｳ"))
        assertEquals(words("ぶ"), words("ぶ"))
        assertEquals(listOf("はーと"), words("ハート"))
        assertEquals(listOf("abc123"), words("ＡＢｃ１２３"))
        assertEquals(listOf("καρδιά", "σοσ"), words("Καρδιά ΣΟΣ"))
        assertEquals(listOf("दिल", "की", "धड़कन"), words("दिल की धड़कन"))
        assertEquals(listOf("i", "istanbul"), words("I İstanbul"))
        assertEquals(listOf("a"), words("á️"))
    }

    @Test
    fun fallbackNeverTakesALanguageKey() {
        val stub = File("src/main/res/raw/annotations.txt").readBytes()
        assertEquals(Annotations.header(Annotations.NONE, "0") + "\n", String(stub))
        assertNull(Annotations.readKey(ByteArrayInputStream(stub)))
        assertNull(Annotations.readKey(ByteArrayInputStream("annotations 2 de 0\n".toByteArray())))
        assertNull(Annotations.readKey(ByteArrayInputStream(ByteArray(0))))
        assertEquals("en", SearchIndex.sourcesKey(arrayOf(TestData.sources[0])))
        val german = TestData.annotations("de")
        assertEquals("en,${german.key}", SearchIndex.sourcesKey(arrayOf(TestData.sources[0], AnnotationSource(TestData.data, german))))
        assertTrue(german.key.startsWith("de@"))
    }
}
