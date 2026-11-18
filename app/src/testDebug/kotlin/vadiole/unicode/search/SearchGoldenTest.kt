package vadiole.unicode.search

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGoldenTest {
    private val files = SuiteFiles.load { name -> requireNotNull(javaClass.getResource("/search/$name")) { "Missing $name" }.readText() }
    private val engine: SearchEngine = TestData.search.let { search ->
        SearchEngine { query, limit ->
            val results = search.search(query, limit)
            SearchHits(results.codePoints, results.total)
        }
    }

    @Test
    fun suiteFilesAreConsistent() {
        val problems = SearchSuite.lint(files)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun replayedGoldensPassTheSuite() {
        val pinned = files.expectations.filter { it.query !in files.allowed && files.queries.none { query -> query.text == it.query && query.mayImprove } }
        val report = SearchSuite.verify(SearchSuite.replay(files.goldens), SuiteFiles(files.queries, files.goldens, pinned, files.weaknesses, emptyMap()))
        assertTrue(report.text, report.passed)
    }

    @Test
    fun shortQueriesSkipGeneratedRanges() {
        val data = TestData.data
        for (query in listOf("1", "2", "a", "c", "h", "s", "cj", "4e")) {
            val found = TestData.search.search(query, 0).codePoints.filter { data.rangeOf(it) >= 0 && (it != query.codePointAt(0) || query.length > 1) }
            assertTrue("$query finds ${found.size} generated names, first ${found.take(4).map(SearchSuite::hex)}", found.isEmpty())
        }
        for (query in listOf("cjk", "hangul", "tangut", "4e00", "arrows", "braille", "中")) {
            assertTrue(query, TestData.search.search(query, 0).total > 0)
        }
    }

    @Test
    fun differencesAreReported() {
        val expected = Golden("q", 3, intArrayOf(1, 2, 3), intArrayOf(1, 2, 3), null)
        val reordered = SearchSuite.diff(expected, Golden("q", 3, intArrayOf(1, 3, 2), intArrayOf(1, 3, 2), null))
        assertTrue(reordered.toString(), reordered.size == 2 && reordered.all { "order changed" in it && "first difference at 2" in it })
        val changed = SearchSuite.diff(expected, Golden("q", 2, intArrayOf(1, 4), intArrayOf(1, 4), null))
        assertTrue(changed.toString(), "total" in changed[0] && "missing 0002 0003" in changed[1] && "extra 0004" in changed[1])
        val text = "  a\\ \t😀\uD800 b "
        assertTrue(SearchSuite.escape(text), SearchSuite.unescape(SearchSuite.escape(text)) == text)
    }

    @Test
    fun engineMatchesGoldens() {
        val report = SearchSuite.verify(engine, files)
        File("build/reports/search").mkdirs()
        File("build/reports/search/verify.txt").writeText(report.text)
        println(report.summary)
        assertTrue(report.text, report.passed)
    }
}
