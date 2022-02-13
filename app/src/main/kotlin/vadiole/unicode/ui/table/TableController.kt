package vadiole.unicode.ui.table

import vadiole.unicode.data.CharStorage
import vadiole.unicode.utils.extension.worker

class TableController(private val charStorage: CharStorage) {
    val totalChars = CharStorage.totalCharacters
    var tableChars: List<String> = listOf()

    suspend fun loadChars() = worker {
        val codePoints = charStorage.getCodePoints()
        tableChars = codePoints.map { String(Character.toChars(it)) }
    }
}