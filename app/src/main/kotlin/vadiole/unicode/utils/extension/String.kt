package vadiole.unicode.utils.extension

fun String.leftPad(size: Int, padChar: Char): String {
    val pads = size - length
    if (pads <= 0) {
        return this
    }
    val buf = CharArray(pads) { padChar }
    return String(buf) + this
}