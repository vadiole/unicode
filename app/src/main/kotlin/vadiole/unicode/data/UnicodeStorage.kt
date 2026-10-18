package vadiole.unicode.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import vadiole.unicode.BuildConfig

class UnicodeStorage(private val context: Context) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var database: SQLiteDatabase? = null

    private suspend fun openDatabase(): SQLiteDatabase = withContext(dispatcher) {
        var currentDatabase = database
        while (currentDatabase == null) {
            currentDatabase = openDatabase(getDatabasePath(), null, OPEN_READONLY)
            database = currentDatabase
        }
        return@withContext currentDatabase
    }

    private suspend fun getDatabasePath(): String = withContext(dispatcher) {
        val databaseDir = File(context.filesDir, databaseDir).also { it.mkdirs() }
        val databaseFile = File(databaseDir, databaseName)
        if (!databaseFile.exists() || databaseFile.length() < 100) {
            databaseDir.listFiles()?.forEach { file ->
                file.delete()
            }
            val temp = File(databaseDir, "$databaseName.tmp")
            try {
                context.assets.open("$databaseName.zip").use { input ->
                    ZipInputStream(input).use { zipInput ->
                        val entry = zipInput.nextEntry
                        if (entry == null || entry.name != databaseName) throw IOException("Missing $databaseName")
                        temp.outputStream().use { output -> zipInput.copyTo(output) }
                    }
                }
                openDatabase(temp.absolutePath, null, OPEN_READWRITE).use { db ->
                    db.beginTransaction()
                    try {
                        db.execSQL("CREATE INDEX code_point_index ON char (code_point)")
                        db.execSQL("CREATE INDEX category_index ON char (category)")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
                if (!temp.renameTo(databaseFile)) throw IOException("Cannot install $databaseName")
            } finally {
                temp.delete()
            }
        }
        return@withContext databaseFile.absolutePath
    }

    suspend fun getCodePoints(count: Int, hasGlyph: (String) -> Boolean): CodePointArray = withContext(dispatcher) {
        val query = if (count > 0) {
            "SELECT code_point, category FROM char WHERE id < ? LIMIT ?"
        } else {
            "SELECT code_point, category FROM char"
        }
        val args = if (count > 0) {
            arrayOf(count.toString(), count.toString())
        } else {
            null
        }
        val result: IntArray
        var size = 0
        openDatabase().rawQuery(query, args).use { cursor ->
            val codePointIndex = cursor.getColumnIndex("code_point")
            val categoryIndex = cursor.getColumnIndex("category")
            result = IntArray(cursor.count)
            while (cursor.moveToNext()) {
                val codePoint = CodePoint(cursor.getInt(codePointIndex))
                if (isShown(codePoint, cursor.getString(categoryIndex), hasGlyph)) {
                    result[size++] = codePoint.value
                }
            }
        }

        return@withContext CodePointArray(result.copyOf(size))
    }

    suspend fun getAbbreviations(): Map<CodePoint, String> = withContext(dispatcher) {
        val result: Map<CodePoint, String>
        openDatabase().rawQuery(queryGetAbbreviations, null).use { cursor ->
            val codePointIndex = cursor.getColumnIndex("code_point")
            val nameIndex = cursor.getColumnIndex("char_name")
            result = HashMap<CodePoint, String>(cursor.count)
            while (cursor.moveToNext()) {
                val codePoint = CodePoint(cursor.getInt(codePointIndex))
                val name = cursor.getString(nameIndex)
                result[codePoint] = ccOverrides[codePoint.value] ?: deriveAbbreviation(name)
            }
        }
        return@withContext result
    }

    private fun deriveAbbreviation(name: String): String {
        val clean = name.substringBefore(" (")
        return buildString {
            var prev = ' '
            for (ch in clean) {
                when {
                    ch.isDigit() -> append(ch)
                    ch.isLetter() && !prev.isLetter() -> append(ch)
                }
                prev = ch
            }
        }.ifEmpty { "?" }
    }

    suspend fun getBlocks(): Array<Block> = withContext(dispatcher) {
        val result: Array<Block>
        openDatabase().rawQuery(queryGetBlocks, null).use { cursor ->
            val idIndex = cursor.getColumnIndex("id")
            val endIndex = cursor.getColumnIndex("end")
            val nameIndex = cursor.getColumnIndex("name")
            var lastEnd = -1
            result = Array(cursor.count) {
                cursor.moveToNext()
                Block(
                    id = cursor.getInt(idIndex),
                    start = lastEnd + 1,
                    end = cursor.getInt(endIndex).also { lastEnd = it },
                    name = cursor.getString(nameIndex),
                )
            }
        }
        return@withContext result
    }

    suspend fun getCharObj(codePoint: CodePoint): CharObj? = withContext(dispatcher) {
        val args = arrayOf(codePoint.value.toString())
        val result: CharObj
        openDatabase().rawQuery(queryGetChar, args).use { cursor ->
            val charIdIndex = cursor.getColumnIndex("char_id")
            val codePointIndex = cursor.getColumnIndex("code_point")
            val charNameIndex = cursor.getColumnIndex("char_name")
            val versionIndex = cursor.getColumnIndex("version")
            val blockNameIndex = cursor.getColumnIndex("block_name")
            if (cursor.count == 0) {
                return@withContext null
            }
            cursor.moveToNext()
            result = CharObj(
                id = cursor.getInt(charIdIndex),
                codePointRaw = cursor.getInt(codePointIndex),
                name = cursor.getString(charNameIndex),
                version = cursor.getString(versionIndex),
                blockName = cursor.getString(blockNameIndex),
            )
        }
        return@withContext result
    }

    suspend fun findCharByCodePoint(codePoint: Int, hasGlyph: (String) -> Boolean): SearchResult? = withContext(dispatcher) {
        val args = arrayOf(codePoint.toString())
        openDatabase().rawQuery(queryFindCharByCodePoint, args).use { cursor ->
            if (cursor.count == 0) return@withContext null
            cursor.moveToFirst()
            val cp = CodePoint(cursor.getInt(cursor.getColumnIndex("code_point")))
            if (!isShown(cp, cursor.getString(cursor.getColumnIndex("category")), hasGlyph)) return@withContext null
            val name = cursor.getString(cursor.getColumnIndex("name"))
            SearchResult(cp, name)
        }
    }

    suspend fun findCharsByCodePoints(codePoints: CodePointArray, hasGlyph: (String) -> Boolean): Map<Int, SearchResult> = withContext(dispatcher) {
        if (codePoints.isEmpty()) return@withContext emptyMap()
        val placeholders = codePoints.joinToString(",") { it.value.toString() }
        val query = "SELECT code_point, name, category FROM char WHERE code_point IN ($placeholders)"
        val result = mutableMapOf<Int, SearchResult>()
        openDatabase().rawQuery(query, null).use { cursor ->
            val cpIndex = cursor.getColumnIndex("code_point")
            val nameIndex = cursor.getColumnIndex("name")
            val categoryIndex = cursor.getColumnIndex("category")
            while (cursor.moveToNext()) {
                val cp = CodePoint(cursor.getInt(cpIndex))
                if (isShown(cp, cursor.getString(categoryIndex), hasGlyph)) {
                    result[cp.value] = SearchResult(cp, cursor.getString(nameIndex))
                }
            }
        }
        result
    }

    suspend fun findCharsByName(input: String, count: Int, hasGlyph: (String) -> Boolean): Array<SearchResult> = withContext(dispatcher) {
        val escaped = escapeLike(input.uppercase())
        val query = if (count > 0) {
            "$queryFindChars LIMIT $count"
        } else {
            queryFindChars
        }
        val args = arrayOf(
            "%$escaped%", "%$escaped%",
            escaped,
            "% $escaped",
            "$escaped %",
            "$escaped%",
            "% $escaped %",
        )
        return@withContext searchByName(query, args, hasGlyph)
    }

    suspend fun findCharsByNameMultiWord(tokens: List<String>, count: Int, hasGlyph: (String) -> Boolean): Array<SearchResult> = withContext(dispatcher) {
        val escapedTokens = tokens.map { escapeLike(it.uppercase()) }
        val nameConditions = escapedTokens.joinToString(" AND ") { "name LIKE ? ESCAPE '\\'" }
        val name2Conditions = escapedTokens.joinToString(" AND ") { "name2 LIKE ? ESCAPE '\\'" }
        val whereClause = "($nameConditions) OR ($name2Conditions)"

        val paddedName = "(' ' || name || ' ')"
        val paddedName2 = "(' ' || IFNULL(name2, '') || ' ')"
        val matchScoreSum = escapedTokens.joinToString(" + ") {
            "(CASE " +
                    "WHEN $paddedName LIKE ? ESCAPE '\\' OR $paddedName2 LIKE ? ESCAPE '\\' THEN 0 " +
                    "WHEN $paddedName LIKE ? ESCAPE '\\' OR $paddedName2 LIKE ? ESCAPE '\\' THEN 1 " +
                    "ELSE 2 END)"
        }

        val orderClause = "ORDER BY ($matchScoreSum), id"

        val sql = "SELECT id, code_point, name, category FROM char WHERE $whereClause $orderClause"
        val limitedSql = if (count > 0) "$sql LIMIT $count" else sql

        val args = mutableListOf<String>()
        for (token in escapedTokens) args.add("%$token%")
        for (token in escapedTokens) args.add("%$token%")
        for (token in escapedTokens) {
            args.add("% $token %")
            args.add("% $token %")
            args.add("% $token%")
            args.add("% $token%")
        }

        return@withContext searchByName(limitedSql, args.toTypedArray(), hasGlyph)
    }

    private suspend fun searchByName(query: String, args: Array<String>, hasGlyph: (String) -> Boolean): Array<SearchResult> {
        val result: ArrayList<SearchResult>
        val millis = measureTimeMillis {
            openDatabase().rawQuery(query, args).use { cursor ->
                result = cursor.toSearchResults(hasGlyph)
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d("UnicodeStorage", "search: ${millis}ms")
        }
        return result.toTypedArray()
    }

    private fun Cursor.toSearchResults(hasGlyph: (String) -> Boolean): ArrayList<SearchResult> {
        val codePointIndex = getColumnIndex("code_point")
        val nameIndex = getColumnIndex("name")
        val categoryIndex = getColumnIndex("category")
        val results = ArrayList<SearchResult>(count)
        while (moveToNext()) {
            val cp = CodePoint(getInt(codePointIndex))
            if (isShown(cp, getString(categoryIndex), hasGlyph)) {
                results.add(SearchResult(cp, getString(nameIndex)))
            }
        }
        return results
    }

    companion object {
        const val totalCharacters = 41341
        private const val databaseDir = "sql"
        private const val databaseName = "u18.sqlite"
        private const val VS_BMP_FIRST = 0xFE00
        private const val VS_BMP_LAST = 0xFE0F
        private const val VS_SUPP_FIRST = 0xE0100
        private const val VS_SUPP_LAST = 0xE01EF
        private const val queryGetChar = "SELECT c.id as char_id, code_point, c.name AS char_name, version, b.name AS block_name " +
                "FROM char c INNER JOIN block b ON c.block_id = b.id " +
                "WHERE code_point = ? LIMIT 1"
        private const val queryGetBlocks = "SELECT id, `end`, name FROM block"
        private const val queryFindCharByCodePoint =
                "SELECT code_point, name, category FROM char WHERE code_point = ? LIMIT 1"
        private const val queryFindChars = "SELECT id, code_point, name, category " +
                "FROM char " +
                "WHERE name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' " +
                "ORDER BY (" +
                "CASE " +
                "WHEN name = ? THEN 1 " +
                "WHEN name LIKE ? ESCAPE '\\' THEN 2 " +
                "WHEN name LIKE ? ESCAPE '\\' THEN 3 " +
                "WHEN name LIKE ? ESCAPE '\\' THEN 4 " +
                "WHEN name LIKE ? ESCAPE '\\' THEN 5 " +
                "ELSE 6 END), " +
                "id"

        private fun escapeLike(input: String): String {
            return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        }

        private fun isShown(codePoint: CodePoint, category: String?, hasGlyph: (String) -> Boolean): Boolean {
            if (category == "Cc" || category == "Cf" || category == "Zl" || category == "Zp") return true
            val cp = codePoint.value
            if (cp in VS_BMP_FIRST..VS_BMP_LAST || cp in VS_SUPP_FIRST..VS_SUPP_LAST) return true
            return hasGlyph(codePoint.char)
        }

        private val queryGetAbbreviations = "SELECT code_point, c.name AS char_name " +
                "FROM char c " +
                "WHERE category IN ('Cc', 'Cf', 'Zl', 'Zp') " +
                "OR (code_point BETWEEN $VS_BMP_FIRST AND $VS_BMP_LAST) " +
                "OR (code_point BETWEEN $VS_SUPP_FIRST AND $VS_SUPP_LAST)"

        private val ccOverrides: Map<Int, String> = mapOf(
            0x00 to "NUL", 0x01 to "SOH", 0x02 to "STX", 0x03 to "ETX",
            0x04 to "EOT", 0x05 to "ENQ", 0x06 to "ACK", 0x07 to "BEL",
            0x08 to "BS", 0x09 to "HT", 0x0A to "LF", 0x0B to "VT",
            0x0C to "FF", 0x0D to "CR", 0x0E to "SO", 0x0F to "SI",
            0x10 to "DLE", 0x11 to "DC1", 0x12 to "DC2", 0x13 to "DC3",
            0x14 to "DC4", 0x15 to "NAK", 0x16 to "SYN", 0x17 to "ETB",
            0x18 to "CAN", 0x19 to "EM", 0x1A to "SUB", 0x1B to "ESC",
            0x1C to "FS", 0x1D to "GS", 0x1E to "RS", 0x1F to "US",
            0x7F to "DEL",
            0x80 to "PAD", 0x81 to "HOP", 0x82 to "BPH", 0x83 to "NBH",
            0x84 to "IND", 0x85 to "NEL", 0x86 to "SSA", 0x87 to "ESA",
            0x88 to "HTS", 0x89 to "HTJ", 0x8A to "VTS", 0x8B to "PLD",
            0x8C to "PLU", 0x8D to "RI", 0x8E to "SS2", 0x8F to "SS3",
            0x90 to "DCS", 0x91 to "PU1", 0x92 to "PU2", 0x93 to "STS",
            0x94 to "CCH", 0x95 to "MW", 0x96 to "SPA", 0x97 to "EPA",
            0x98 to "SOS", 0x99 to "SGC", 0x9A to "SCI", 0x9B to "CSI",
            0x9C to "ST", 0x9D to "OSC", 0x9E to "PM", 0x9F to "APC",
        )
    }
}