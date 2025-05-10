package vadiole.unicode.data

import java.util.Locale

class CharObj(val id: Int, val codePointRaw: Int, val name: String, val version: String, val blockName: String) {
    val char: String = String(Character.toChars(codePointRaw))
    val hex = codePointRaw.toString(16).uppercase(Locale.ENGLISH).leftPad(4, '0')
    val infoValues: Array<String> = arrayOf(
        "U+$hex",
        "&#$codePointRaw",
        "\\$hex",
        version,
    )

    fun getLink(): String = "unicode.vadiole.me/share?c=$codePointRaw"
}


fun String.leftPad(size: Int, padChar: Char): String {
    val pads = size - length
    if (pads <= 0) {
        return this
    }
    val buf = CharArray(pads) { padChar }
    return String(buf) + this
}