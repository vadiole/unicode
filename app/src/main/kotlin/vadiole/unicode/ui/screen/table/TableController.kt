package vadiole.unicode.ui.screen.table

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vadiole.unicode.data.CharStorage

class TableController(private val charStorage: CharStorage) {
    val totalChars = charStorage.totalCharacters
    var tableChars: List<String> = listOf()

    suspend fun loadChars(): List<String> {
        val codePoints = charStorage.getCodePoints()
        tableChars = withContext(Dispatchers.Default) {
            codePoints.map { String(Character.toChars(it)) }
        }
        return tableChars
    }

    suspend fun getDescription(id: Int): String {
        return charStorage.getDescription(id)
    }

    suspend fun getCodePoint(id: Int): Int {
        return charStorage.getCodePoint(id)
    }
}