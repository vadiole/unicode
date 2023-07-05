package vadiole.unicode.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import java.io.File
import vadiole.unicode.utils.extension.io

class UnicodeStorage(private val context: Context) {
    var database: SQLiteDatabase? = null

    //TODO: is it okay to use just io dispatcher?
    private suspend fun openDatabase(): SQLiteDatabase = io {
        var currentDatabase = database
        while (currentDatabase == null) {
            currentDatabase = openDatabase(getDatabasePath(), null, OPEN_READONLY)
            database = currentDatabase
        }
        return@io currentDatabase
    }

    private suspend fun getDatabasePath(): String = io {
        context.filesDir.mkdirs()
        val database = File(context.filesDir, databaseName)
        if (!database.exists() || database.length() < 100) {
            context.filesDir.listFiles()!!.forEach { file ->
                file.deleteOnExit()
            }
            context.assets.open(databaseName).use { input ->
                database.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return@io database.absolutePath
    }

    suspend fun getCodePoints(count: Int): CodePointArray = io {
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

        return@io result
    }

    suspend fun getBlocks(): Array<Block> = io {
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
        return@io result
    }

    suspend fun getCharObj(codePoint: CodePoint): CharObj = io {
        val args = arrayOf(codePoint.value.toString())
        val result: CharObj
        openDatabase().rawQuery(queryGetChar, args).use { cursor ->
            val charIdIndex = cursor.getColumnIndex("char_id")
            val codePointIndex = cursor.getColumnIndex("code_point")
            val charNameIndex = cursor.getColumnIndex("char_name")
            val versionIndex = cursor.getColumnIndex("version")
            val blockNameIndex = cursor.getColumnIndex("block_name")
            cursor.moveToNext()
            result = CharObj(
                id = cursor.getInt(charIdIndex),
                codePointRaw = cursor.getInt(codePointIndex),
                name = cursor.getString(charNameIndex),
                version = cursor.getString(versionIndex),
                blockName = cursor.getString(blockNameIndex),
            )
        }
        return@io result
    }

    suspend fun findCharsByName(input: String, count: Int): Array<SearchResult> = io {
        val query = if (count > 0) {
            "$queryFindChars LIMIT $count"
        } else {
            queryFindChars
        }
        val args = arrayOf(
            "% $input",
            "$input %",
            "% $input %",
            "%$input%",
            input, "% $input", "$input %", "% $input %",
        )
        val result: Array<SearchResult>
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
        return@io result
    }

    companion object {
        const val totalCharacters = 34920
        private const val databaseName = "u15.sqlite"
        private const val queryGetChar = "SELECT c.id as char_id, code_point, c.name AS char_name, version, b.name AS block_name " +
                "FROM char c INNER JOIN block b ON c.block_id = b.id " +
                "WHERE code_point = ? LIMIT 1"
        private const val queryGetBlocks = "SELECT id, `end`, name FROM block"
        private const val queryFindChars = "SELECT id, code_point, name " +
                "FROM char " +
                "WHERE name LIKE ? " +
                "OR name LIKE ? " +
                "OR name Like ? " +
                "OR name LIKE ? " +
                "ORDER BY (CASE WHEN name = ? THEN 1 WHEN name LIKE ? THEN 2 WHEN name LIKE ? THEN 3 WHEN name = ? THEN 4 ELSE 5 END), id"
    }
}
