package vadiole.unicode.search

class Query(val text: String, val category: String, val mayImprove: Boolean)

class Golden(val query: String, val total: Int, val first: IntArray, val top: IntArray, val error: String?)

class Expectation(val source: String, val query: String, val top: Int, val codePoints: IntArray, val above: IntArray)

class SuiteFiles(
    val queries: List<Query>,
    val goldens: Map<String, Golden>,
    val expectations: List<Expectation>,
    val weaknesses: List<Expectation>,
    val allowed: Map<String, String>,
) {
    companion object {
        fun load(read: (String) -> String) = SuiteFiles(
            queries = SearchSuite.parseQueries(read("queries.txt")),
            goldens = SearchSuite.parseGoldens(read("goldens.txt")),
            expectations = SearchSuite.parseExpectations(read("expectations.txt")),
            weaknesses = SearchSuite.parseExpectations(read("weaknesses.txt")),
            allowed = SearchSuite.parseAllowed(read("allowed.txt")),
        )
    }
}

class SuiteReport(val passed: Boolean, val summary: String, val text: String)

object SearchSuite {
    const val FIRST_LIMIT = 64
    const val TOP_SIZE = 200
    private const val LISTED = 8

    fun escape(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            val edge = index == 0 || index == text.length - 1 || text[index - 1] == ' '
            when {
                cp == '\\'.code -> append("\\\\")
                cp == ' '.code && edge -> append("\\s")
                cp == '#'.code && index == 0 -> append("\\x{23}")
                cp == ' '.code || cp in 0x21..0x7E -> append(cp.toChar())
                else -> append("\\x{").append(hex(cp)).append('}')
            }
            index += Character.charCount(cp)
        }
    }

    fun unescape(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val c = text[index]
            if (c != '\\') {
                append(c)
                index++
                continue
            }
            when (text.getOrNull(index + 1)) {
                '\\' -> append('\\')
                's' -> append(' ')
                'x' -> {
                    val end = text.indexOf('}', index)
                    require(text.getOrNull(index + 2) == '{' && end > 0) { "Bad escape in $text" }
                    appendCodePoint(text.substring(index + 3, end).toInt(16))
                    index = end + 1
                    continue
                }
                else -> throw IllegalArgumentException("Bad escape in $text")
            }
            index += 2
        }
    }

    fun hex(codePoint: Int): String = codePoint.toString(16).uppercase().padStart(4, '0')

    fun parseQueries(text: String): List<Query> = dataLines(text).map { line ->
        val tag = line.substringBefore(' ')
        val flags = tag.split('+')
        require(flags.drop(1).all { it == "improve" }) { "Unknown flag in $line" }
        Query(unescape(line.substringAfter(' ', "")), flags[0], flags.size > 1)
    }

    fun parseGoldens(text: String): Map<String, Golden> {
        val result = LinkedHashMap<String, Golden>()
        for (line in dataLines(text)) {
            val fields = line.split('\t')
            val query = unescape(fields[0])
            require(query !in result) { "Duplicate golden ${fields[0]}" }
            result[query] = if (fields[1] == "error") {
                Golden(query, -1, IntArray(0), IntArray(0), fields[2])
            } else {
                require(fields.size == 4) { "Bad golden $line" }
                Golden(query, fields[1].toInt(), parseCodePoints(fields[2], ' '), parseCodePoints(fields[3], ' '), null)
            }
        }
        return result
    }

    fun format(golden: Golden): String {
        val query = escape(golden.query)
        if (golden.error != null) return "$query\terror\t${golden.error}"
        return "$query\t${golden.total}\t${formatCodePoints(golden.first)}\t${formatCodePoints(golden.top)}"
    }

    fun parseExpectations(text: String): List<Expectation> = dataLines(text).map { line ->
        val fields = line.split(' ', limit = 3)
        require(fields.size >= 2) { "Bad expectation $line" }
        val query = unescape(fields.getOrElse(2) { "" })
        val rule = fields[0]
        when {
            rule.startsWith("top:") -> Expectation(line, query, rule.substring(4).toInt(), parseCodePoints(fields[1], ','), IntArray(0))
            rule == "above" -> {
                val pairs = fields[1].split(',').flatMap { pair ->
                    val sides = pair.split('>')
                    require(sides.size == 2) { "Bad expectation $line" }
                    sides.map { it.toInt(16) }
                }
                Expectation(line, query, 0, IntArray(0), pairs.toIntArray())
            }
            else -> throw IllegalArgumentException("Bad expectation $line")
        }
    }

    fun parseAllowed(text: String): Map<String, String> = dataLines(text).associate { line ->
        val reason = line.substringAfter('\t', "").trim()
        require(reason.isNotEmpty()) { "Allowed difference without a reason: $line" }
        unescape(line.substringBefore('\t')) to reason
    }

    fun capture(engine: SearchEngine, query: String): Golden = try {
        val first = engine.search(query, FIRST_LIMIT)
        val all = engine.search(query, 0)
        Golden(query, all.total, first.codePoints, all.codePoints.copyOf(minOf(all.codePoints.size, TOP_SIZE)), null)
    } catch (e: Exception) {
        val message = e.message.orEmpty().lineSequence().firstOrNull().orEmpty().replace('\t', ' ').take(80)
        Golden(query, -1, IntArray(0), IntArray(0), "${e.javaClass.simpleName}: $message")
    }

    fun replay(goldens: Map<String, Golden>) = SearchEngine { query, limit ->
        val golden = goldens.getValue(query)
        check(golden.error == null) { golden.error.orEmpty() }
        if (limit > 0) SearchHits(golden.first, golden.first.size) else SearchHits(golden.top, golden.total)
    }

    fun diff(expected: Golden, actual: Golden): List<String> {
        if (expected.error != null || actual.error != null) {
            if (expected.error != null && actual.error != null) return emptyList()
            return listOf("error: expected ${expected.error ?: "results"}, got ${actual.error ?: "results"}")
        }
        val result = ArrayList<String>()
        if (expected.total != actual.total) result.add("total: expected ${expected.total}, got ${actual.total}")
        diffList("first $FIRST_LIMIT", expected.first, actual.first, result)
        diffList("top $TOP_SIZE", expected.top, actual.top, result)
        return result
    }

    private fun diffList(label: String, expected: IntArray, actual: IntArray, out: MutableList<String>) {
        if (expected.contentEquals(actual)) return
        val missing = expected.filter { it !in actual }
        val extra = actual.filter { it !in expected }
        var position = 0
        while (position < expected.size && position < actual.size && expected[position] == actual[position]) position++
        val parts = ArrayList<String>()
        if (missing.isNotEmpty()) parts.add("missing ${listed(missing)}")
        if (extra.isNotEmpty()) parts.add("extra ${listed(extra)}")
        if (missing.isEmpty() && extra.isEmpty()) parts.add("order changed")
        parts.add("first difference at ${position + 1}: expected ${at(expected, position)}, got ${at(actual, position)}")
        out.add("$label: ${parts.joinToString("; ")}")
    }

    private fun listed(codePoints: List<Int>): String {
        val shown = codePoints.take(LISTED).joinToString(" ") { hex(it) }
        return if (codePoints.size > LISTED) "$shown and ${codePoints.size - LISTED} more" else shown
    }

    private fun at(codePoints: IntArray, position: Int) = if (position < codePoints.size) hex(codePoints[position]) else "end"

    fun check(expectation: Expectation, hits: IntArray): String? {
        val problems = ArrayList<String>()
        for (cp in expectation.codePoints) {
            val index = hits.indexOf(cp)
            if (index < 0) problems.add("${hex(cp)} missing") else if (index >= expectation.top) problems.add("${hex(cp)} at ${index + 1}")
        }
        for (i in expectation.above.indices step 2) {
            val upper = hits.indexOf(expectation.above[i])
            val lower = hits.indexOf(expectation.above[i + 1])
            if (upper < 0) {
                problems.add("${hex(expectation.above[i])} missing")
            } else if (lower in 0 until upper) {
                problems.add("${hex(expectation.above[i])} at ${upper + 1} below ${hex(expectation.above[i + 1])} at ${lower + 1}")
            }
        }
        return if (problems.isEmpty()) null else problems.joinToString(", ")
    }

    private fun check(expectation: Expectation, golden: Golden): String? {
        if (golden.error != null) return golden.error
        val first = if (expectation.top in 1..FIRST_LIMIT) check(expectation, golden.first)?.let { "first $FIRST_LIMIT: $it" } else null
        return first ?: check(expectation, golden.top)
    }

    fun verify(engine: SearchEngine, files: SuiteFiles): SuiteReport {
        val failed = ArrayList<String>()
        val improved = ArrayList<String>()
        val allowed = ArrayList<String>()
        val captured = HashMap<String, Golden>()
        var matched = 0
        for (query in files.queries) {
            val name = escape(query.text)
            val actual = captured.getOrPut(query.text) { capture(engine, query.text) }
            val expected = files.goldens[query.text]
            val reason = files.allowed[query.text]
            val differences = if (expected == null) listOf("no golden") else diff(expected, actual)
            val entry = "[${query.category}] $name\n" + differences.joinToString("") { "    $it\n" }
            when {
                differences.isEmpty() && reason != null -> failed.add("[${query.category}] $name\n    matches the golden, remove it from allowed.txt\n")
                differences.isEmpty() -> matched++
                reason != null -> allowed.add("$entry    allowed: $reason\n")
                query.mayImprove -> improved.add(entry)
                else -> failed.add(entry)
            }
        }
        val broken = ArrayList<String>()
        for (expectation in files.expectations) {
            val problem = check(expectation, captured.getOrPut(expectation.query) { capture(engine, expectation.query) })
            if (problem != null) broken.add("${expectation.source}\n    $problem\n")
        }
        val open = ArrayList<String>()
        val fixed = ArrayList<String>()
        for (weakness in files.weaknesses) {
            val problem = check(weakness, captured.getOrPut(weakness.query) { capture(engine, weakness.query) })
            if (problem != null) open.add("${weakness.source}\n    $problem\n") else fixed.add("${weakness.source}\n")
        }
        val passed = failed.isEmpty() && broken.isEmpty()
        val summary = (if (passed) "PASS" else "FAIL") +
                " queries ${files.queries.size}: $matched match, ${allowed.size} allowed, ${improved.size} may improve, ${failed.size} fail;" +
                " expectations ${files.expectations.size}: ${broken.size} fail;" +
                " weaknesses ${files.weaknesses.size}: ${fixed.size} fixed, ${open.size} open"
        val text = buildString {
            appendLine(summary)
            section("Differences from the goldens", failed)
            section("Failed ranking expectations", broken)
            section("Allowed differences", allowed)
            section("Differences on queries that may improve", improved)
            section("Fixed weaknesses", fixed)
            section("Open weaknesses", open)
        }
        return SuiteReport(passed, summary, text)
    }

    private fun StringBuilder.section(title: String, entries: List<String>) {
        if (entries.isEmpty()) return
        appendLine()
        appendLine("$title (${entries.size})")
        entries.forEach { append(it) }
    }

    fun lint(files: SuiteFiles): List<String> {
        val problems = ArrayList<String>()
        val queries = HashMap<String, Query>()
        for (query in files.queries) {
            if (queries.put(query.text, query) != null) problems.add("duplicate query ${escape(query.text)}")
            if (query.text !in files.goldens) problems.add("no golden for ${escape(query.text)}")
        }
        for (golden in files.goldens.values) {
            if (golden.query !in queries) problems.add("golden without a query ${escape(golden.query)}")
            if (golden.top.size != minOf(golden.total, TOP_SIZE) && golden.error == null) problems.add("golden total does not fit its list ${escape(golden.query)}")
        }
        for (query in files.allowed.keys) {
            if (query !in queries) problems.add("allowed difference without a query ${escape(query)}")
        }
        for (expectation in files.expectations) {
            val golden = files.goldens[expectation.query]
            if (golden == null) {
                problems.add("expectation without a query: ${expectation.source}")
            } else if (expectation.query !in files.allowed && queries[expectation.query]?.mayImprove != true) {
                check(expectation, golden)?.let { problems.add("expectation fails on the golden: ${expectation.source}: $it") }
            }
        }
        for (weakness in files.weaknesses) {
            val golden = files.goldens[weakness.query]
            when {
                golden == null -> problems.add("weakness without a query: ${weakness.source}")
                queries[weakness.query]?.mayImprove != true -> problems.add("weakness query is not marked +improve: ${weakness.source}")
                check(weakness, golden) == null -> problems.add("weakness already holds on the golden: ${weakness.source}")
            }
        }
        return problems
    }

    private fun dataLines(text: String): List<String> = text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun formatCodePoints(codePoints: IntArray) = if (codePoints.isEmpty()) "-" else codePoints.joinToString(" ") { hex(it) }

    private fun parseCodePoints(text: String, separator: Char): IntArray {
        if (text == "-") return IntArray(0)
        val result = ArrayList<Int>()
        for (part in text.split(separator)) {
            val bounds = part.split("..")
            val start = bounds[0].toInt(16)
            for (cp in start..bounds.getOrElse(1) { bounds[0] }.toInt(16)) result.add(cp)
        }
        return result.toIntArray()
    }
}
