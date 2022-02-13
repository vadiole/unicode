package vadiole.unicode.data

import vadiole.unicode.utils.extension.leftPad
import java.util.*

class CharObj(val id: Int, val codePoint: Int, val description: String) {
    val char: String = String(Character.toChars(codePoint))
    val hex = codePoint.toString(16).uppercase(Locale.ENGLISH).leftPad(4, '0')
    val infoValues: Array<String> = arrayOf(
        "U+$hex",
        "&#$codePoint",
        "\\$hex",
        "1.0",
    )
}