package vadiole.unicode.utils.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

inline fun <T> with(receiver: T?, block: T.() -> Unit) {
    if (receiver != null) {
        block.invoke(receiver)
    }
}


suspend inline fun <T> io(noinline block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO, block)
}

suspend inline fun <T> worker(noinline block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.Default, block)
}
