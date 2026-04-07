package vadiole.unicode.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.util.Log
import java.io.File
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
                file.deleteOnExit()
            }
            context.assets.open("$databaseName.zip").use { input ->
                ZipInputStream(input).use { zipInput ->
                    val entry = zipInput.nextEntry
                    if (entry != null && entry.name == databaseName) {
                        databaseFile.outputStream().use { output ->
                            zipInput.copyTo(output)
                        }
                    }
                }
            }
        }
        return@withContext databaseFile.absolutePath
    }

    suspend fun getCodePoints(count: Int): CodePointArray = withContext(dispatcher) {
        val query = if (count > 0) {
            "SELECT code_point FROM char WHERE id < ? LIMIT ?"
        } else {
            "SELECT code_point FROM char"
        }
        val args = if (count > 0) {
            arrayOf(count.toString(), count.toString())
        } else {
            null
        }
        val result: CodePointArray
        openDatabase().rawQuery(query, args).use { cursor ->
            val codePointIndex = cursor.getColumnIndex("code_point")
            result = CodePointArray(cursor.count) {
                cursor.moveToNext()
                CodePoint(value = cursor.getInt(codePointIndex))
            }
        }

        return@withContext result
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
                val abbreviation = name
                    .substringBefore(" (")
                    .split(" ")
                    .joinToString("") { word ->
                        word.first().toString()
                    }
                result[codePoint] = abbreviation
            }
        }
        return@withContext result
    }

    suspend fun getBlocks(): Array<Block> = withContext(dispatcher) {
        val result: Array<Block>
        openDatabase().rawQuery(queryGetBlocks, null).use { cursor ->
            val idIndex = cursor.getColumnIndex("id")
            val endIndex = cursor.getColumnIndex("end")
            val nameIndex = cursor.getColumnIndex("name")
            var lastEnd = 0
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
            if (!hasGlyph(cp.char)) return@withContext null
            val name = cursor.getString(cursor.getColumnIndex("name"))
            SearchResult(cp, name)
        }
    }

    suspend fun findCharsByCodePoints(codePoints: CodePointArray, hasGlyph: (String) -> Boolean): Map<Int, SearchResult> = withContext(dispatcher) {
        if (codePoints.isEmpty()) return@withContext emptyMap()
        val placeholders = codePoints.joinToString(",") { it.value.toString() }
        val query = "SELECT code_point, name FROM char WHERE code_point IN ($placeholders)"
        val result = mutableMapOf<Int, SearchResult>()
        openDatabase().rawQuery(query, null).use { cursor ->
            val cpIndex = cursor.getColumnIndex("code_point")
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val cp = CodePoint(cursor.getInt(cpIndex))
                if (hasGlyph(cp.char)) {
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
            escaped, escaped,
            "% $escaped", "% $escaped",
            "$escaped%", "$escaped%",
            "$escaped %", "$escaped %",
            "% $escaped %", "% $escaped %",
        )
        return@withContext searchByName(query, args, hasGlyph)
    }

    suspend fun findCharsByNameMultiWord(tokens: List<String>, count: Int, hasGlyph: (String) -> Boolean): Array<SearchResult> = withContext(dispatcher) {
        val escapedTokens = tokens.map { escapeLike(it.uppercase()) }
        val nameConditions = escapedTokens.joinToString(" AND ") { "name LIKE ? ESCAPE '\\'" }
        val name2Conditions = escapedTokens.joinToString(" AND ") { "name2 LIKE ? ESCAPE '\\'" }
        val whereClause = "($nameConditions) OR ($name2Conditions)"

        val lastToken = escapedTokens.last()
        val orderClause = "ORDER BY (" +
                "CASE " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 1 " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 2 " +
                "ELSE 3 END), " +
                "id"

        val sql = "SELECT id, code_point, name FROM char WHERE $whereClause $orderClause"
        val limitedSql = if (count > 0) "$sql LIMIT $count" else sql

        val args = mutableListOf<String>()
        for (token in escapedTokens) args.add("%$token%")
        for (token in escapedTokens) args.add("%$token%")
        args.add("% $lastToken")
        args.add("% $lastToken")
        args.add("$lastToken%")
        args.add("$lastToken%")

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
        val results = ArrayList<SearchResult>(count)
        while (moveToNext()) {
            val cp = CodePoint(getInt(codePointIndex))
            if (hasGlyph(cp.char)) {
                results.add(SearchResult(cp, getString(nameIndex)))
            }
        }
        return results
    }

    companion object {
        const val totalCharacters = 40116
        private const val databaseDir = "sql"
        private const val databaseName = "u16.sqlite"
        private const val queryGetChar = "SELECT c.id as char_id, code_point, c.name AS char_name, version, b.name AS block_name " +
                "FROM char c INNER JOIN block b ON c.block_id = b.id " +
                "WHERE code_point = ? LIMIT 1"
        private const val queryGetBlocks = "SELECT id, `end`, name FROM block"
        private const val queryFindCharByCodePoint =
                "SELECT code_point, name FROM char WHERE code_point = ? LIMIT 1"
        private const val queryFindChars = "SELECT id, code_point, name " +
                "FROM char " +
                "WHERE name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' " +
                "ORDER BY (" +
                "CASE " +
                "WHEN name = ? OR name2 = ? THEN 1 " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 2 " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 3 " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 4 " +
                "WHEN name LIKE ? ESCAPE '\\' OR name2 LIKE ? ESCAPE '\\' THEN 5 " +
                "ELSE 6 END), " +
                "id"

        private fun escapeLike(input: String): String {
            return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        }
        private const val queryGetAbbreviations = "SELECT code_point, c.name AS char_name " +
                "FROM char c " +
                "WHERE code_point <= 159 AND (code_point >= 127 OR (code_point >> 5) = 0)"
    }
}