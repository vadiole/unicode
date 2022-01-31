package vadiole.unicode.utils.ktx

inline fun <T> with(receiver: T?, block: T.() -> Unit) {
    if (receiver != null) {
        block.invoke(receiver)
    }
}