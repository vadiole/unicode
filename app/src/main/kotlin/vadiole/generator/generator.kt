import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import vadiole.unicode.search.SearchTokens
import vadiole.unicode.data.Annotations
import vadiole.unicode.data.UnicodeData
import vadiole.unicode.data.UnicodeData.Companion.HAN_CORE
import vadiole.unicode.data.UnicodeData.Companion.HAN_OTHER
import vadiole.unicode.data.UnicodeData.Companion.HAN_SCHOOL
import vadiole.unicode.data.UnicodeData.Companion.KEYWORD_DISPLAY
import vadiole.unicode.data.UnicodeData.Companion.KIND_ABBREVIATION
import vadiole.unicode.data.UnicodeData.Companion.KIND_NORMAL
import vadiole.unicode.data.UnicodeData.Companion.RANGE_HANGUL
import vadiole.unicode.data.UnicodeData.Companion.RANGE_HEX
import vadiole.unicode.data.UnicodeData.Companion.SECOND_ABBREVIATION
import vadiole.unicode.data.UnicodeData.Companion.SECOND_ALIAS
import vadiole.unicode.data.UnicodeData.Companion.SECOND_ALTERNATE
import vadiole.unicode.data.UnicodeData.Companion.SECOND_CONTROL
import vadiole.unicode.data.UnicodeData.Companion.SECOND_CORRECTION
import vadiole.unicode.data.UnicodeData.Companion.SECOND_FIGMENT
import vadiole.unicode.data.UnicodeData.Companion.SECOND_SHORT_NAME
import vadiole.unicode.data.UnicodeData.Companion.SECOND_UNICODE_1

class GeneratorBlock(val start: Int, val end: Int, val name: String)

class GeneratorRange(val first: Int, val last: Int, val type: Int, val prefix: String)

class GeneratorText(val codePoint: Int, val tag: Int, val text: String)

class GeneratorData(
    val codePoints: IntArray,
    val kinds: IntArray,
    val versions: Array<String>,
    val blocks: List<GeneratorBlock>,
    val subheads: List<GeneratorBlock>,
    val ranges: List<GeneratorRange>,
    val names: List<String>,
    val secondNames: List<GeneratorText>,
    val keywords: List<GeneratorText>,
    val meanings: List<GeneratorText>,
    val inherited: List<Int> = emptyList(),
)

// NR1 and NR2 prefixes from https://www.unicode.org/Public/18.0.0/ucd/extracted/DerivedName.txt
private val rangePrefixes = listOf(
    "Hangul Syllable" to "HANGUL SYLLABLE",
    "CJK Ideograph" to "CJK UNIFIED IDEOGRAPH",
    "Tangut Ideograph" to "TANGUT IDEOGRAPH",
    "Jurchen Character" to "JURCHEN CHARACTER",
    "Seal Character" to "SMALL SEAL CHARACTER",
)

// https://www.unicode.org/Public/18.0.0/ucd/PropList.txt Prepended_Concatenation_Mark: visible Cf glyphs
private val prependedConcatenationMarks = intArrayOf(
    0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x06DD, 0x070F, 0x0890, 0x0891, 0x08E2, 0x110BD, 0x110CD,
)

private val controlNames = mapOf(
    0x80 to "PADDING CHARACTER",
    0x81 to "HIGH OCTET PRESET",
    0x84 to "INDEX",
    0x99 to "SINGLE GRAPHIC CHARACTER INTRODUCER",
)

private val aliasTypes = mapOf(
    "correction" to SECOND_CORRECTION,
    "control" to SECOND_CONTROL,
    "alternate" to SECOND_ALTERNATE,
    "figment" to SECOND_FIGMENT,
    "abbreviation" to SECOND_ABBREVIATION,
)

private val variantPrefixes = listOf("HEAVY ", "LIGHT ", "BLACK ", "WHITE ")

private val aliasContinuations = setOf("before", "after", "of", "or", "and", "in", "as", "with", "to", "for", "from", "by", "on", "at")

private val hanSchoolFields = setOf("kGradeLevel", "kJoyoKanji", "kKoreanEducationHanja", "kHanyuPinlu")

fun main() {
    val root = "app/src/main/kotlin/vadiole/generator"
    val data = readSources(File("$root/input"))
    val annotations = writeAnnotations(File("$root/input/annotations"), File("app/src/main/res"), data.codePoints.toHashSet())
    val sections = encodeSections(data)
    val bytes = encodeFile(sections)
    File("app/src/main/assets/u18.bin").writeBytes(bytes)
    File("$root/output").mkdirs()
    File("$root/output/keywords.txt").printWriter().use { out ->
        var last = -1
        for (keyword in data.keywords) {
            if (keyword.codePoint != last) {
                if (last >= 0) out.println()
                out.print("%04X".format(keyword.codePoint))
                last = keyword.codePoint
            }
            out.print(if (keyword.tag == KEYWORD_DISPLAY) " | " else " ~ ")
            out.print(keyword.text)
        }
        out.println()
    }
    File("$root/output/summary.txt").printWriter().use { out ->
        out.println("data version ${UnicodeData.VERSION}")
        out.println("characters ${data.codePoints.size}, in generated ranges ${data.ranges.sumOf { it.last - it.first + 1 }}, names ${data.names.size}")
        out.println("blocks ${data.blocks.size}, subheads ${data.subheads.size}, versions ${data.versions.distinct().size}")
        out.println("second names ${data.secondNames.size}: " + secondNameLabels.mapIndexed { tag, label -> "$label ${data.secondNames.count { it.tag == tag }}" }.joinToString())
        out.println("keywords ${data.keywords.size}, displayed ${data.keywords.count { it.tag == KEYWORD_DISPLAY }}, characters ${data.keywords.distinctBy { it.codePoint }.size}")
        out.println("meanings ${data.meanings.size}")
        out.println("keywords inherited from a heavy, light, black or white variant: " + data.inherited.joinToString(" ") { "%04X".format(it) })
        out.println()
        out.println("section raw deflated")
        sections.forEachIndexed { index, section ->
            out.println("${sectionLabels[index]} ${section.size} ${deflatedSize(section)}")
        }
        out.println("file ${bytes.size} ${deflatedSize(bytes)}")
        out.println()
        out.println("annotations characters keywords displayed raw deflated")
        annotations.forEach { out.println(it) }
    }
    print(File("$root/output/summary.txt").readText())
}

private val secondNameLabels = listOf("unicode1", "correction", "control", "alternate", "figment", "abbreviation", "alias", "short")

private val sectionLabels = listOf(
    "code_points", "kinds", "ages", "age_text", "blocks", "block_text", "subheads", "subhead_text", "ranges", "range_text",
    "names", "second_names", "second_name_text", "keywords", "keyword_text", "meanings", "meaning_text",
)

private fun deflatedSize(bytes: ByteArray): Int {
    val deflater = Deflater(9, true)
    deflater.setInput(bytes)
    deflater.finish()
    val buffer = ByteArray(1 shl 16)
    var size = 0
    while (!deflater.finished()) size += deflater.deflate(buffer)
    deflater.end()
    return size
}

private fun readSources(input: File): GeneratorData {
    val blocks = File(input, "block.txt").readCsv(";").map { fields ->
        val limits = fields[0].split("..")
        GeneratorBlock(limits[0].toInt(16), limits[1].toInt(16), fields[1])
    }
    val ages = File(input, "version.txt").readCsv(";").map { fields ->
        val limits = fields[0].split("..")
        GeneratorBlock(limits[0].toInt(16), limits.last().toInt(16), fields[1])
    }
    val versionList = ages.map { it.name }.distinct().sortedBy { it.toDouble() }
    val ageOf = HashMap<Int, Int>()
    for (age in ages) for (codePoint in age.start..age.end) ageOf[codePoint] = versionList.indexOf(age.name)

    val codePoints = ArrayList<Int>()
    val kinds = ArrayList<Int>()
    val ranges = ArrayList<GeneratorRange>()
    val names = ArrayList<String>()
    val formal = HashMap<Int, String>()
    val secondNames = HashMap<Int, ArrayList<GeneratorText>>()

    fun addSecond(codePoint: Int, tag: Int, text: String) {
        if (text.isEmpty() || text.equals(formal[codePoint], ignoreCase = true)) return
        val list = secondNames.getOrPut(codePoint) { ArrayList() }
        if (list.none { it.text.equals(text, ignoreCase = true) }) list.add(GeneratorText(codePoint, tag, text))
    }

    val lines = File(input, "data.txt").readCsv(";")
    var index = 0
    while (index < lines.size) {
        val fields = lines[index++]
        val codePoint = fields[0].toInt(16)
        val category = fields[2]
        var name = fields[1]
        if (name.endsWith(", First>")) {
            val last = lines[index++]
            val label = name.removePrefix("<").removeSuffix(", First>")
            check(last[1] == "<$label, Last>") { "range $label has no end" }
            if (category == "Co" || category == "Cs") continue
            val prefix = rangePrefixes.find { label.startsWith(it.first) }?.second ?: error("unknown name derivation for range $label")
            val end = last[0].toInt(16)
            ranges.add(GeneratorRange(codePoint, end, if (prefix == "HANGUL SYLLABLE") RANGE_HANGUL else RANGE_HEX, prefix))
            for (cp in codePoint..end) {
                codePoints.add(cp)
                kinds.add(KIND_NORMAL)
            }
            continue
        }
        check(category != "Co" && category != "Cs") { "unexpected $category character $codePoint" }
        var unicode1 = fields[10]
        if (name == "<control>") {
            name = controlNames[codePoint] ?: unicode1
            unicode1 = ""
        }
        check(name.isNotEmpty() && !name.startsWith("<")) { "char with code point $codePoint has no name" }
        val abbreviated = category == "Cc" || category == "Cf" || category == "Zl" || category == "Zp" ||
                codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF
        codePoints.add(codePoint)
        kinds.add(if (abbreviated && codePoint !in prependedConcatenationMarks) KIND_ABBREVIATION else KIND_NORMAL)
        names.add(name)
        formal[codePoint] = name
        addSecond(codePoint, SECOND_UNICODE_1, unicode1)
    }
    val assigned = codePoints.toHashSet()
    val versions = IntArray(codePoints.size) { ageOf[codePoints[it]] ?: error("code point ${codePoints[it]} has no age") }

    File(input, "aliases.txt").readCsv(";").forEach { fields ->
        addSecond(fields[0].toInt(16), aliasTypes.getValue(fields[2]), fields[1])
    }

    val subheads = ArrayList<GeneratorBlock>()
    var heading: String? = null
    var headingStart = -1
    var headingEnd = -1
    var current = -1
    fun closeHeading() {
        val name = heading
        if (name != null && headingStart >= 0) subheads.add(GeneratorBlock(headingStart, headingEnd, name))
        heading = null
        headingStart = -1
    }
    File(input, "names.txt").forEachLine { line ->
        when {
            line.startsWith("@@\t") -> closeHeading()
            line.startsWith("@\t\t") -> {
                closeHeading()
                heading = line.substring(3).trim()
            }
            line.startsWith("\t= ") -> if (current in formal) {
                for (alias in splitAliases(line.substring(3))) addSecond(current, SECOND_ALIAS, alias)
            }
            line.isNotEmpty() -> {
                current = line.trim().toInt(16)
                if (current in assigned && heading != null) {
                    if (headingStart < 0) headingStart = current
                    headingEnd = current
                }
            }
        }
    }
    closeHeading()
    subheads.sortBy { it.start }

    val shortNames = HashMap<Int, String>()
    val rawKeywords = LinkedHashMap<Int, List<String>>()
    readAnnotations(File(input, "annotations.xml"), assigned, shortNames, rawKeywords)
    for ((codePoint, shortName) in shortNames) addSecond(codePoint, SECOND_SHORT_NAME, shortName)
    val byName = formal.entries.associate { it.value to it.key }
    val inherited = ArrayList<Int>()
    for (codePoint in rawKeywords.keys.toList()) {
        val name = formal[codePoint] ?: continue
        val variant = variantPrefixes.firstOrNull { name.startsWith(it) } ?: continue
        val base = byName[name.substring(variant.length)] ?: continue
        if (base in rawKeywords || base in shortNames) continue
        rawKeywords[base] = rawKeywords.getValue(codePoint).filter { !it.equals(variant.trim(), ignoreCase = true) }
        inherited.add(base)
    }
    val keywords = ArrayList<GeneratorText>()
    for (codePoint in rawKeywords.keys.sorted()) {
        val formalName = formal[codePoint] ?: rangeName(ranges, codePoint)
        val ordered = orderKeywords(rawKeywords.getValue(codePoint), formalName, shortNames[codePoint].orEmpty())
        ordered.forEach { (keyword, display) -> keywords.add(GeneratorText(codePoint, if (display) KEYWORD_DISPLAY else 0, keyword)) }
    }

    val definitions = sortedMapOf<Int, String>()
    val school = HashSet<Int>()
    val core = HashSet<Int>()
    File(input, "unihan.txt").forEachLine { line ->
        if (!line.startsWith("U+")) return@forEachLine
        val parts = line.split('\t')
        val codePoint = parts[0].substring(2).toInt(16)
        when (parts[1]) {
            "kDefinition" -> definitions[codePoint] = parts[2].trim()
            "kUnihanCore2020" -> core.add(codePoint)
            in hanSchoolFields -> school.add(codePoint)
        }
    }
    val meanings = definitions.filterKeys { it in assigned }.map { (codePoint, text) ->
        GeneratorText(codePoint, if (codePoint in school) HAN_SCHOOL else if (codePoint in core) HAN_CORE else HAN_OTHER, text)
    }

    return GeneratorData(
        codePoints = codePoints.toIntArray(),
        kinds = kinds.toIntArray(),
        versions = Array(codePoints.size) { versionList[versions[it]] },
        blocks = blocks,
        subheads = subheads,
        ranges = ranges,
        names = names,
        secondNames = secondNames.keys.sorted().flatMap { secondNames.getValue(it) },
        keywords = keywords,
        meanings = meanings,
        inherited = inherited.sorted(),
    )
}

private val annotationLine = Regex("<annotation cp=\"([^\"]+)\"( type=\"tts\")?>([^<]*)</annotation>")

private const val INHERITED = "↑↑↑"

private fun readAnnotations(file: File, assigned: Set<Int>, shortNames: MutableMap<Int, String>, keywords: MutableMap<Int, List<String>>) {
    file.forEachLine { line ->
        val match = annotationLine.find(line) ?: return@forEachLine
        val cp = unescapeXml(match.groupValues[1]).replace("\uFE0F", "")
        if (cp.isEmpty() || cp.codePointCount(0, cp.length) != 1) return@forEachLine
        val codePoint = cp.codePointAt(0)
        if (codePoint !in assigned) return@forEachLine
        val text = unescapeXml(match.groupValues[3]).trim()
        if (text == INHERITED) return@forEachLine
        if (match.groupValues[2].isNotEmpty()) {
            shortNames[codePoint] = text
        } else {
            keywords[codePoint] = text.split('|').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
    }
}

fun annotationQualifiers(res: File, prefix: String): List<String> {
    return res.listFiles().orEmpty().filter { it.name.startsWith("$prefix-") && it.list().orEmpty().any { name -> name == "strings.xml" || name.startsWith("annotations") } }
        .map { it.name.removePrefix("$prefix-") }.sorted()
}

fun languageTag(qualifier: String) = qualifier.replace("-r", "-")

private fun foldedWords(text: String): List<String> {
    val tokens = SearchTokens(Int.MAX_VALUE, distinct = false)
    tokens.set(text)
    return List(tokens.count) { tokens.token(it) }
}

fun orderLocalizedKeywords(keywords: List<String>, shortName: String): Pair<List<String>, List<String>> {
    val name = foldedWords(shortName)
    val worded = ArrayList<String>()
    val symbolic = ArrayList<String>()
    val hidden = ArrayList<String>()
    for (keyword in keywords) {
        val hasLetter = keyword.any { it.isLetter() }
        val hasDigit = keyword.any { it.isDigit() }
        when {
            (hasLetter || hasDigit) && foldedWords(keyword) == name -> Unit
            keyword.all { it.isDigit() } -> hidden.add(keyword)
            !hasLetter && !hasDigit && keyword.codePointCount(0, keyword.length) == 1 -> hidden.add(keyword)
            hasLetter -> worded.add(keyword)
            else -> symbolic.add(keyword)
        }
    }
    return (worded + symbolic) to hidden
}

fun encodeAnnotations(tag: String, shortNames: Map<Int, String>, keywords: Map<Int, List<String>>): ByteArray {
    val body = StringBuilder()
    for (codePoint in (shortNames.keys + keywords.keys).sorted()) {
        val name = shortNames[codePoint].orEmpty()
        val (displayed, hidden) = orderLocalizedKeywords(keywords[codePoint].orEmpty(), name)
        val fields = listOf(name, displayed.joinToString("|"), hidden.joinToString("|"))
        require(fields.none { '\t' in it || '\n' in it })
        body.append(codePoint.toString(16).uppercase()).append('\t').append(fields.joinToString("\t")).append('\n')
    }
    val bytes = body.toString().toByteArray()
    val checksum = CRC32().also { it.update(bytes) }
    return (Annotations.header(tag, "%08x".format(checksum.value)) + "\n").toByteArray() + bytes
}

private fun writeAnnotations(input: File, res: File, assigned: Set<Int>): List<String> {
    val summary = ArrayList<String>()
    File(res, "raw/annotations.txt").writeText(Annotations.header(Annotations.NONE, "0") + "\n")
    for (qualifier in annotationQualifiers(res, "values")) {
        val shortNames = HashMap<Int, String>()
        val keywords = HashMap<Int, List<String>>()
        val parent = File(input, "$qualifier.parent.xml")
        if (parent.exists()) readAnnotations(parent, assigned, shortNames, keywords)
        readAnnotations(File(input, "$qualifier.xml"), assigned, shortNames, keywords)
        check(shortNames.size > 1000) { "annotations for $qualifier look incomplete: ${shortNames.size} names" }
        val bytes = encodeAnnotations(languageTag(qualifier), shortNames, keywords)
        File(res, "raw-$qualifier").mkdirs()
        File(res, "raw-$qualifier/annotations.txt").writeBytes(bytes)
        val displayed = keywords.entries.sumOf { orderLocalizedKeywords(it.value, shortNames[it.key].orEmpty()).first.size }
        summary.add("$qualifier ${(shortNames.keys + keywords.keys).size} ${keywords.values.sumOf { it.size }} $displayed ${bytes.size} ${deflatedSize(bytes)}")
    }
    val values = annotationQualifiers(res, "values")
    val raw = annotationQualifiers(res, "raw")
    check(values == raw) { "locale folders differ: values-* $values, raw-* $raw" }
    return summary
}

private fun rangeName(ranges: List<GeneratorRange>, codePoint: Int): String {
    return ranges.firstOrNull { codePoint in it.first..it.last }?.prefix.orEmpty()
}

private fun unescapeXml(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

fun splitAliases(line: String): List<String> {
    val parts = ArrayList<String>()
    var depth = 0
    var start = 0
    for (i in line.indices) {
        when (line[i]) {
            '(' -> depth++
            ')' -> depth--
            ',' -> if (depth == 0) {
                parts.add(line.substring(start, i))
                start = i + 1
            }
        }
    }
    parts.add(line.substring(start))
    val names = parts.map { it.trim() }.filter { it != "etc." }
    val whole = names.drop(1).any { it.substringBefore(' ').lowercase() in aliasContinuations }
    return (if (whole) listOf(names.joinToString(", ")) else names).map { it.removeSuffix("(1.0)").removeSuffix("(1.1)").trim() }.filter { it.isNotEmpty() }
}

private val vowels = "aeiouy".toSet()

private fun stem(input: String): Pair<String, Boolean> {
    val word = input.lowercase()
    if (word.length <= 3) return word to false
    var stem = word
    var cut = true
    when {
        word.endsWith("ies") && word.length > 4 -> stem = word.dropLast(3) + "y"
        word.endsWith("ing") || word.endsWith("ful") -> stem = word.dropLast(3)
        word.endsWith("ed") -> stem = word.dropLast(2)
        word.endsWith("es") && listOf("s", "x", "z", "ch", "sh").any { word.dropLast(2).endsWith(it) } -> stem = word.dropLast(2)
        word.endsWith("s") && listOf("ss", "us", "is").none { word.endsWith(it) } -> stem = word.dropLast(1)
        else -> cut = false
    }
    if (cut && (stem.length < 3 || stem.none { it in vowels })) {
        stem = word
        cut = false
    }
    if (cut && stem.length > 3 && stem[stem.length - 1] == stem[stem.length - 2] && stem.last() !in vowels) stem = stem.dropLast(1)
    if (stem.length > 4 && (stem.last() == 'y' || stem.last() == 'i')) stem = stem.dropLast(1)
    if (stem.length > 4 && stem.last() == 'e') stem = stem.dropLast(1)
    return stem to cut
}

private fun editDistanceAtMostOne(first: String, second: String): Boolean {
    if (first == second) return true
    val a = if (first.length <= second.length) first else second
    val b = if (first.length <= second.length) second else first
    if (b.length - a.length > 1) return false
    var i = 0
    while (i < a.length && a[i] == b[i]) i++
    return if (a.length == b.length) a.substring(i + 1) == b.substring(i + 1) else a.substring(i) == b.substring(i + 1)
}

fun sameKeywordWord(first: String, second: String): Boolean {
    val a = first.lowercase()
    val b = second.lowercase()
    if (a == b) return true
    val (stemA, cutA) = stem(a)
    val (stemB, cutB) = stem(b)
    if (stemA == stemB || (cutA && stemA + "e" == stemB) || (cutB && stemB + "e" == stemA)) return true
    if (a[0] != b[0]) return false
    return (minOf(a.length, b.length) >= 5 && editDistanceAtMostOne(a, b)) ||
            (minOf(stemA.length, stemB.length) >= 5 && editDistanceAtMostOne(stemA, stemB))
}

private val keywordWordBreak = Regex("[^\\p{L}\\p{N}_']+")

private fun keywordWords(text: String) = text.lowercase().replace('’', '\'').split(keywordWordBreak).filter { it.isNotEmpty() }

fun orderKeywords(keywords: List<String>, formalName: String, shortName: String): List<Pair<String, Boolean>> {
    val pool = keywordWords(formalName) + keywordWords(shortName)
    val worded = ArrayList<String>()
    val symbolic = ArrayList<String>()
    val hidden = ArrayList<String>()
    for (keyword in keywords) {
        val words = keywordWords(keyword)
        val hasLetter = keyword.any { it.isLetter() }
        val hasDigit = keyword.any { it.isDigit() }
        when {
            words.isNotEmpty() && words.all { word -> pool.any { sameKeywordWord(word, it) } } -> hidden.add(keyword)
            keyword.all { it.isDigit() } -> hidden.add(keyword)
            !hasLetter && !hasDigit && keyword.codePointCount(0, keyword.length) == 1 -> hidden.add(keyword)
            hasLetter -> worded.add(keyword)
            else -> symbolic.add(keyword)
        }
    }
    return worded.map { it to true } + symbolic.map { it to true } + hidden.map { it to false }
}

private class Section {
    val bytes = ByteArrayOutputStream()

    fun varint(value: Int) {
        var v = value
        require(v >= 0)
        while (v >= 0x80) {
            bytes.write(v or 0x80)
            v = v ushr 7
        }
        bytes.write(v)
    }

    fun byte(value: Int) = bytes.write(value)

    fun line(text: String) {
        require('\n' !in text)
        bytes.write(text.toByteArray())
        bytes.write('\n'.code)
    }
}

private fun runs(values: IntArray): Section {
    val section = Section()
    var count = 0
    for (i in values.indices) if (i == 0 || values[i] != values[i - 1]) count++
    section.varint(count)
    var start = 0
    for (i in 1..values.size) {
        if (i == values.size || values[i] != values[start]) {
            section.byte(values[start])
            section.varint(i - start)
            start = i
        }
    }
    return section
}

private fun texts(entries: List<GeneratorText>): Pair<Section, Section> {
    val meta = Section()
    val text = Section()
    meta.varint(entries.size)
    var last = 0
    for (entry in entries) {
        meta.varint(entry.codePoint - last)
        meta.byte(entry.tag)
        text.line(entry.text)
        last = entry.codePoint
    }
    return meta to text
}

private fun spans(entries: List<GeneratorBlock>): Pair<Section, Section> {
    val meta = Section()
    val text = Section()
    meta.varint(entries.size)
    var last = 0
    for (entry in entries) {
        meta.varint(entry.start - last)
        meta.varint(entry.end - entry.start)
        text.line(entry.name)
        last = entry.start
    }
    return meta to text
}

fun encodeSections(data: GeneratorData): List<ByteArray> {
    val codePoints = Section()
    val starts = data.codePoints.indices.filter { it == 0 || data.codePoints[it] != data.codePoints[it - 1] + 1 }
    codePoints.varint(starts.size)
    var next = 0
    starts.forEachIndexed { run, start ->
        val end = if (run + 1 < starts.size) starts[run + 1] else data.codePoints.size
        codePoints.varint(data.codePoints[start] - next)
        codePoints.varint(end - start - 1)
        next = data.codePoints[end - 1] + 1
    }
    val versionList = data.versions.distinct()
    val ageText = Section().apply { versionList.forEach { line(it) } }
    val ages = runs(IntArray(data.versions.size) { versionList.indexOf(data.versions[it]) })
    val (blocks, blockText) = spans(data.blocks)
    val (subheads, subheadText) = spans(data.subheads)
    val ranges = Section()
    val rangeText = Section()
    ranges.varint(data.ranges.size)
    for (range in data.ranges) {
        ranges.varint(range.first)
        ranges.varint(range.last - range.first)
        ranges.byte(range.type)
        rangeText.line(range.prefix)
    }
    val names = Section().apply { data.names.forEach { line(it) } }
    val (secondNames, secondNameText) = texts(data.secondNames)
    val (keywords, keywordText) = texts(data.keywords)
    val (meanings, meaningText) = texts(data.meanings)
    return listOf(
        codePoints, runs(data.kinds), ages, ageText, blocks, blockText, subheads, subheadText, ranges, rangeText,
        names, secondNames, secondNameText, keywords, keywordText, meanings, meaningText,
    ).map { it.bytes.toByteArray() }
}

fun encodeFile(sections: List<ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    val out = DataOutputStream(bytes)
    val checksum = CRC32()
    sections.forEach { checksum.update(it) }
    out.writeInt(UnicodeData.MAGIC)
    out.writeInt(UnicodeData.VERSION)
    out.writeInt(checksum.value.toInt())
    out.writeInt(sections.size)
    sections.forEach { out.writeInt(it.size) }
    sections.forEach { out.write(it) }
    return bytes.toByteArray()
}

private fun File.readCsv(delimiter: String): List<List<String>> {
    return readLines()
        .filter { line -> line.isNotBlank() && !line.startsWith("#") }
        .map { line -> line.substringBefore("#").split(delimiter).map { it.trim() } }
}
