package vadiole.unicode.data

import android.content.Context
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

    suspend fun findCharsByName(input: String, count: Int): Array<SearchResult> = withContext(dispatcher) {
        val query = if (count > 0) {
            "$queryFindChars LIMIT $count"
        } else {
            queryFindChars
        }
        val args = arrayOf(
            "%$input%",
            input, "% $input", "$input %", "$input%", "% $input %",
        )
        val result: Array<SearchResult>
        measureTimeMillis {
            openDatabase().rawQuery(query, args).use { cursor ->
                val rowsCount = cursor.count
                val codePointIndex = cursor.getColumnIndex("code_point")
                val nameIndex = cursor.getColumnIndex("name")
                result = Array(rowsCount) {
                    cursor.moveToPosition(it)
                    val codePoint = cursor.getInt(codePointIndex)
                    val name = cursor.getString(nameIndex)
                    SearchResult(CodePoint(codePoint), name)
                }
            }
        }.also {
            Log.d("UnicodeStorage", "perf $it")
        }
        return@withContext result
    }

    companion object {
        const val totalCharacters = 40116
        private const val databaseDir = "sql"
        private const val databaseName = "u16.sqlite"
        private const val queryGetChar = "SELECT c.id as char_id, code_point, c.name AS char_name, version, b.name AS block_name " +
                "FROM char c INNER JOIN block b ON c.block_id = b.id " +
                "WHERE code_point = ? LIMIT 1"
        private const val queryGetBlocks = "SELECT id, `end`, name FROM block"
        private const val queryFindChars = "SELECT id, code_point, name " +
                "FROM char " +
                "WHERE name LIKE ? " +
                "ORDER BY (" +
                "CASE " +
                "WHEN name = ? THEN 1 " +
                "WHEN name LIKE ? THEN 2 " +
                "WHEN name LIKE ? THEN 4 " +
                "WHEN name LIKE ? THEN 3 " +
                "WHEN name LIKE ? THEN 5 " +
                "ELSE 6 END), " +
                "id"
    }
}