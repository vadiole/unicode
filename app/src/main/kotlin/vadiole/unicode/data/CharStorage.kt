package vadiole.unicode.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import vadiole.unicode.utils.extension.io
import java.io.File

class CharStorage(private val context: Context) {
    var database: SQLiteDatabase? = null

    private suspend fun openDatabase(): SQLiteDatabase = io {
        var currentDatabase = database
        if (currentDatabase == null) {
            currentDatabase = openDatabase(getDatabasePath(), null, OPEN_READONLY)!!
            database = currentDatabase
        }
        return@io currentDatabase
    }

    @Suppress("BlockingMethodInNonBlockingContext")
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

    suspend fun getCodePoints(): MutableList<Int> = io {
        val query = "SELECT code_point FROM chars"
        val result = mutableListOf<Int>()
        openDatabase().rawQuery(query, null).use { cursor ->
            val index = cursor.getColumnIndex("code_point")
            while (cursor.moveToNext()) {
                val codePoint = cursor.getInt(index)
                result.add(codePoint)
            }
        }
        return@io result
    }

    suspend fun getCodePoint(id: Int): Int = io {
        val query = "SELECT code_point FROM chars WHERE id = ? LIMIT 1"
        val args = arrayOf(id.toString())
        val result: Int
        openDatabase().rawQuery(query, args).use { cursor ->
            val index = cursor.getColumnIndex("code_point")
            cursor.moveToNext()
            result = cursor.getInt(index)
        }
        return@io result
    }

    suspend fun getDescription(id: Int): String = io {
        val query = "SELECT description FROM chars WHERE id = ? LIMIT 1"
        val args = arrayOf(id.toString())
        var result: String
        openDatabase().rawQuery(query, args).use { cursor ->
            val index = cursor.getColumnIndex("description")
            cursor.moveToNext()
            result = cursor.getString(index)
        }
        return@io result
    }

    suspend fun getCharObj(id: Int): CharObj = io {
        val query = "SELECT id, code_point, description FROM chars WHERE id = ? LIMIT 1"
        val args = arrayOf(id.toString())
        val result: CharObj
        openDatabase().rawQuery(query, args).use { cursor ->
            val codePointIndex = cursor.getColumnIndex("code_point")
            val descriptionIndex = cursor.getColumnIndex("description")
            cursor.moveToNext()
            result = CharObj(
                id = id,
                codePoint = cursor.getInt(codePointIndex),
                description = cursor.getString(descriptionIndex)
            )
        }
        return@io result
    }

    companion object {
        const val totalCharacters = 34622
        const val databaseName = "u14_v2.sqlite"
    }
}