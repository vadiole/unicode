import java.io.File
import java.io.PrintWriter

class Version(
    val start: Int,
    val end: Int?,
    val version: String,
) {
    fun contains(codePoint: Int): Boolean {
        return if (end == null) {
            codePoint == start
        } else {
            codePoint >= start && codePoint <= end
        }
    }
}

class Block(
    val id: Int,
    val start: Int,
    val end: Int,
    val name: String,
) {
    fun print(out: PrintWriter) {
        out.println("$id;$name;$start;$end")
    }
}

class Char(
    val id: Int,
    val codePoint: Int,
    val name: String,
    val name2: String,
    val version: String,
    val blockId: Int,
) {
    fun print(out: PrintWriter) {
        out.println("$id;$codePoint;$name;$name2;$version;$blockId")
    }
}

fun main() {
    val root = "app/src/main/kotlin/vadiole/generator"
    val blocks = File("$root/input/block.txt")
        .readCsv(";")
        .mapIndexed { id, lineData ->
            val limits = lineData.first().split("..")
            val start = limits[0].toInt(16)
            val end = limits[1].toInt(16)
            val name = lineData[1]
            Block(id, start, end, name)
        }
    val versions = File("$root/input/version.txt")
        .readCsv(";")
        .map { lineData ->
            val limits = lineData.first().split("..")
            return@map if (limits.size == 1) {
                Version(
                    limits[0].toInt(16),
                    null,
                    lineData[1],
                )
            } else {
                Version(
                    limits[0].toInt(16),
                    limits[1].toInt(16),
                    lineData[1],
                )
            }
        }
    val chars = File("$root/input/data.txt")
        .readCsv(";")
        .mapIndexed { id, lineData ->
            val codePoint = lineData[0].toInt(16)
            var name = lineData[1]
            var name2 = lineData[10]
            if (name == "<control>") {
                name = name2
                name2 = ""
                // chars 128, 129, 132 has no names in unicode data
                when (codePoint) {
                    128 -> {
                        name = "PADDING CHARACTER"
                    }

                    129 -> {
                        name = "HIGH OCTET PRESET"
                    }

                    132 -> {
                        name = "INDEX"
                    }

                    153 -> {
                        name = "SINGLE GRAPHIC CHARACTER INTRODUCER"
                    }
                }
            }
            if (name.isEmpty()) {
                throw RuntimeException("char with code point $codePoint has no name")
            }
            val blockId = blocks
                .find { block ->
                    codePoint >= block.start && codePoint <= block.end
                }!!
                .id
            val version = versions
                .find { version ->
                    version.contains(codePoint)
                }?.version ?: "1.0"
            Char(id, codePoint, name, name2, version, blockId)
        }

    File("$root/output/char.csv").printWriter().use { out ->
        chars.forEach { char ->
            char.print(out)
        }
    }

    File("$root/output/block.csv").printWriter().use { out ->
        blocks.forEach { block ->
            block.print(out)
        }
    }
}

fun File.readCsv(delimiter: String): List<List<String>> {
    return readLines()
        .filterNot { line ->
            line.startsWith("#")
        }
        .filter { line ->
            line.isNotBlank()
        }
        .map { line ->
            line
                .substringBefore("#")
                .split(delimiter)
                .map { string ->
                    string.trim()
                }
        }
}