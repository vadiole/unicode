import java.io.File

object BlockIconsGenerator {
    private const val CANDIDATES = 8
    private val invisibleCategories = setOf("Cc", "Cf", "Zs", "Zl", "Zp", "Co", "Cs")

    private class Span(val start: Int, val end: Int, val name: String)

    private class Curated(val label: String?, val type: Char?, val codePoints: List<Int>)

    @JvmStatic
    fun main(args: Array<String>) {
        val root = "app/src/main/kotlin/vadiole/generator"
        val input = File("$root/input")
        val categories = HashMap<Int, String>()
        val lines = File(input, "data.txt").readLines().map { it.split(';') }
        var index = 0
        while (index < lines.size) {
            val fields = lines[index++]
            val codePoint = fields[0].toInt(16)
            if (fields[1].endsWith(", First>")) {
                val end = lines[index++][0].toInt(16)
                if (fields[2] != "Co" && fields[2] != "Cs") for (cp in codePoint..end) categories[cp] = fields[2]
            } else {
                categories[codePoint] = fields[2]
            }
        }
        val blocks = readRanges(File(input, "block.txt")).map { (range, name) -> Span(range.first, range.last, name) }
        val ignorable = readRanges(File(input, "ignorable.txt")).flatMap { it.first }.toHashSet()
        fun visible(cp: Int): Boolean {
            val category = categories[cp] ?: return false
            return category !in invisibleCategories && cp !in ignorable
        }

        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        val curated = LinkedHashMap<Int, Curated>()
        File(input, "block_icons.txt").readLines().forEachIndexed { number, line ->
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@forEachIndexed
            val start = tokens[0].toIntOrNull(16)
            if (start == null) {
                errors += "line ${number + 1}: bad block start ${tokens[0]}"
                return@forEachIndexed
            }
            var label: String? = null
            var type: Char? = null
            val codePoints = ArrayList<Int>()
            for (token in tokens.drop(1)) {
                if (token.length > 2 && token.startsWith('[') && token.endsWith(']')) {
                    label = token.substring(1, token.length - 1)
                    continue
                }
                if (token.length > 2 && token.startsWith('(') && token.endsWith(')')) {
                    type = when (token) {
                        "(single)" -> 's'
                        "(grid)" -> 'y'
                        else -> null.also { errors += "line ${number + 1}: unknown type $token, expected (single) or (grid)" }
                    }
                    continue
                }
                val cp = token.codePointAt(0)
                if (token.length != Character.charCount(cp)) errors += "line ${number + 1}: token $token is not one character"
                else codePoints += cp
            }
            if (start in curated) errors += "line ${number + 1}: block %04X listed twice".format(start)
            curated[start] = Curated(label, type, codePoints)
        }
        val starts = blocks.map { it.start }.toSet()
        for (start in curated.keys) if (start !in starts) errors += "%04X is not a block start".format(start)

        val out = StringBuilder()
        val report = StringBuilder()
        val counts = IntArray(4)
        val few = ArrayList<String>()
        for (block in blocks) {
            val assigned = (block.start..block.end).filter { it in categories }
            val shown = assigned.filter(::visible)
            val letters = shown.count { categories.getValue(it)[0] == 'L' }
            val marks = shown.count { categories.getValue(it)[0] == 'M' }
            val line = curated[block.start]
            val label = line?.label
            val listed = line?.codePoints.orEmpty()
            if (block.start !in curated && assigned.isNotEmpty()) warnings += "no line for %04X %s".format(block.start, block.name)
            val picks = ArrayList<Int>()
            for (cp in listed) {
                val where = "%04X in %s".format(cp, block.name)
                when {
                    cp !in block.start..block.end -> errors += "$where: outside the block"
                    cp !in categories -> errors += "$where: unassigned"
                    !visible(cp) -> errors += "$where: invisible (${categories[cp]})"
                    cp in picks -> warnings += "$where: listed twice"
                    else -> picks += cp
                }
            }
            val type = when {
                label != null || shown.isEmpty() -> 'n'
                line?.type != null -> line.type
                letters * 2 >= shown.size -> 's'
                (letters + marks) * 2 >= shown.size && letters * 5 >= shown.size -> 's'
                marks * 2 >= shown.size -> 'k'
                else -> 'y'
            }
            if (type == 'n' && label == null && assigned.isNotEmpty()) errors += "%04X %s has nothing visible and no [label]".format(block.start, block.name)
            for (cp in shown) {
                if (picks.size >= CANDIDATES || type == 'n') break
                if (cp !in picks) picks += cp
            }
            if (assigned.isEmpty()) continue
            counts["nsky".indexOf(type)]++
            if (type != 'n' && shown.size < 4) few += "%04X %s (%d)".format(block.start, block.name, shown.size)
            out.append(Integer.toHexString(block.start).uppercase()).append(' ').append(type).append(' ').append(label ?: "-")
            for (cp in picks) {
                out.append(' ').append(Integer.toHexString(cp).uppercase())
                if (categories.getValue(cp)[0] == 'M') out.append('m')
            }
            out.append('\n')
            report.append("%05X %s%s %-48s L%3d%% M%3d%% of %5d  %s\n".format(
                block.start, type, if (line?.type != null) "*" else " ", block.name, letters * 100 / maxOf(1, shown.size), marks * 100 / maxOf(1, shown.size), shown.size,
                picks.take(4).joinToString(" ") { cp -> (if (categories.getValue(cp)[0] == 'M') "◌" else "") + String(Character.toChars(cp)) },
            ))
        }
        File("app/src/main/assets/block_icons.txt").writeText(out.toString())
        File("$root/output").mkdirs()
        File("$root/output/block_icons.txt").writeText(
            "types: n nothing visible ${counts[0]}, s script ${counts[1]}, k marks ${counts[2]}, y symbols ${counts[3]}, * set in the source\n" +
                "fewer than 4 visible characters: ${few.joinToString()}\n\n" + report
        )
        warnings.forEach { println("warning: $it") }
        errors.forEach { println("error: $it") }
        if (errors.isNotEmpty()) kotlin.system.exitProcess(1)
    }

    private fun readRanges(file: File): List<Pair<IntRange, String>> = file.readLines().mapNotNull { line ->
        val content = line.substringBefore('#').trim()
        if (content.isEmpty()) return@mapNotNull null
        val fields = content.split(';').map { it.trim() }
        val limits = fields[0].split("..")
        limits[0].toInt(16)..limits.last().toInt(16) to fields[1]
    }
}
