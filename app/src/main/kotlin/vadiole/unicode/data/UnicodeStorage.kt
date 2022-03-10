package vadiole.unicode.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import android.util.Log
import vadiole.unicode.utils.extension.io
import java.io.File

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
            @Suppress("BlockingMethodInNonBlockingContext")
            context.assets.open(databaseName).use { input ->
                database.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return@io database.absolutePath
    }

    suspend fun getCodePoints(count: Int): List<CodePoint> = io {
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
        val result = mutableListOf<CodePoint>()
        openDatabase().rawQuery(query, args).use { cursor ->
            val codePointIndex = cursor.getColumnIndex("code_point")
            while (cursor.moveToNext()) {
                val codePoint = CodePoint(
                    value = cursor.getInt(codePointIndex),
                )
                result.add(codePoint)
            }
        }
        return@io result
    }

    suspend fun getBlocks(): List<Block> = io {
        Log.d("THREAD", Thread.currentThread().toString())
        val query = "SELECT id, `end`, name FROM block"
        val result = mutableListOf<Block>()
        openDatabase().rawQuery(query, null).use { cursor ->
            val idIndex = cursor.getColumnIndex("id")
            val endIndex = cursor.getColumnIndex("end")
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val block = Block(
                    id = cursor.getInt(idIndex),
                    start = (result.lastOrNull()?.end ?: -1) + 1,
                    end = cursor.getInt(endIndex),
                    name = cursor.getString(nameIndex),
                )
                result.add(block)
            }
        }
        return@io result
    }

    suspend fun getCharObj(codePoint: CodePoint): CharObj = io {
        val query = "SELECT c.id as char_id, code_point, c.name AS char_name, version, b.name AS block_name " +
                "FROM char c INNER JOIN block b ON c.block_id = b.id " +
                "WHERE code_point = ? LIMIT 1"
        val args = arrayOf(codePoint.value.toString())
        val result: CharObj
        openDatabase().rawQuery(query, args).use { cursor ->
            val chadIdIndex = cursor.getColumnIndex("char_id")
            val codePointIndex = cursor.getColumnIndex("code_point")
            val charNameIndex = cursor.getColumnIndex("char_name")
            val versionIndex = cursor.getColumnIndex("version")
            val blockNameIndex = cursor.getColumnIndex("block_name")
            cursor.moveToNext()
            result = CharObj(
                id = cursor.getInt(chadIdIndex),
                codePoint = cursor.getInt(codePointIndex),
                name = cursor.getString(charNameIndex),
                version = cursor.getString(versionIndex),
                blockName = cursor.getString(blockNameIndex),
            )
        }
        return@io result
    }

    companion object {
        const val totalCharacters = 34920
        private const val databaseName = "u15.sqlite"
    }
}