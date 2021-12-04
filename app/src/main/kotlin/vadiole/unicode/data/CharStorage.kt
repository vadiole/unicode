package vadiole.unicode.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.openDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

class CharStorage(private val context: Context, private val ioDispatcher: CoroutineDispatcher) {
    val totalCharacters = 34622
    var database: SQLiteDatabase? = null

    private suspend fun openDatabase(): SQLiteDatabase = withContext(ioDispatcher) {
        var currentDatabase = database
        if (currentDatabase == null) {
            currentDatabase = openDatabase(getDatabasePath(), null, OPEN_READONLY)!!
            database = currentDatabase
        }
        return@withContext currentDatabase
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun getDatabasePath(): String = withContext(ioDispatcher) {
        context.filesDir.mkdirs()
        val database = File(context.filesDir, "u14_v1.sqlite")
        if (!database.exists() || database.length() < 100) {
            context.filesDir.listFiles()!!.forEach { file ->
                file.deleteOnExit()
            }
            context.assets.open("u14_v1.sqlite").use { input ->
                database.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return@withContext database.absolutePath
    }

    suspend fun getCodePoints(): MutableList<Int> = withContext(ioDispatcher) {
        val query = "SELECT codePoint FROM chars"
        val result = mutableListOf<Int>()
        openDatabase().rawQuery(query, null).use { cursor ->
            val index = cursor.getColumnIndex("codePoint")
            while (cursor.moveToNext()) {
                val codePoint = cursor.getInt(index)
                result.add(codePoint)
            }
        }
        return@withContext result
    }

    suspend fun getCodePoint(id: Int): Int = withContext(ioDispatcher) {
        val query = "SELECT codePoint FROM chars WHERE id = ? LIMIT 1"
        val args = arrayOf(id.toString())
        val result: Int
        openDatabase().rawQuery(query, args).use { cursor ->
            val index = cursor.getColumnIndex("codePoint")
            cursor.moveToNext()
            result = cursor.getInt(index)
        }
        return@withContext result
    }

    suspend fun getDescription(id: Int): String = withContext(ioDispatcher) {
        val query = "SELECT description FROM chars WHERE id = ? LIMIT 1"
        val args = arrayOf(id.toString())
        var result: String
        openDatabase().rawQuery(query, args).use { cursor ->
            val index = cursor.getColumnIndex("description")
            cursor.moveToNext()
            result = cursor.getString(index)
        }
        return@withContext result
    }
}