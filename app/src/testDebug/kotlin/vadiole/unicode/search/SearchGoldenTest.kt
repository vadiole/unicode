package vadiole.unicode.search

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SearchGoldenTest {
    private val files = SuiteFiles.load { name -> requireNotNull(javaClass.getResource("/search/$name")) { "Missing $name" }.readText() }
    private val engine: SearchEngine? = null

    @Test
    fun suiteFilesAreConsistent() {
        val problems = SearchSuite.lint(files)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun replayedGoldensPassTheSuite() {
        val report = SearchSuite.verify(SearchSuite.replay(files.goldens), files)
        assertTrue(report.text, report.passed)
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
        assumeTrue("SKIPPED: no search engine runs on the JVM yet, the SQLite search is checked on a device with tools/search-golden.sh verify", engine != null)
        val report = SearchSuite.verify(requireNotNull(engine), files)
        println(report.text)
        assertTrue(report.text, report.passed)
    }
}
