package vadiole.unicode.data


@JvmInline
value class CodePoint(val value: Int) {
    val char: String
        get() = String(Character.toChars(value))
}