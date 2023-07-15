package vadiole.unicode.data

import java.util.Locale
import vadiole.unicode.utils.extension.leftPad

class CharObj(val id: Int, val codePointRaw: Int, val name: String, val version: String, val blockName: String) {
    val char: String = String(Character.toChars(codePointRaw))
    val hex = codePointRaw.toString(16).uppercase(Locale.ENGLISH).leftPad(4, '0')
    val infoValues: Array<String> = arrayOf(
        "U+$hex",
        "&#$codePointRaw",
        "\\$hex",
        version,
    )
    val link = "vadiole.github.io/unicode?c=$codePointRaw"
}
