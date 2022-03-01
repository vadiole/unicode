package vadiole.unicode.ui.table

import vadiole.unicode.data.CharStorage
import vadiole.unicode.utils.extension.worker

class TableController(private val charStorage: CharStorage) {
    var totalChars = CharStorage.totalCharacters
    var tableCodePoints: List<Int> = listOf()

    suspend fun loadChars() = worker {
        val codePoints = charStorage.getCodePoints()
        tableCodePoints = codePoints
        totalChars = codePoints.size
    }
}