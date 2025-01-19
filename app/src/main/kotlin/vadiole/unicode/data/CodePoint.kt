package vadiole.unicode.data

@JvmInline
value class CodePoint(val value: Int) {
    val char: String
        get() = String(Character.toChars(value))
}

@JvmInline
value class CodePointArray(val storage: IntArray) : Collection<CodePoint> {
    constructor(size: Int) : this(IntArray(size))

    override val size: Int get() = storage.size

    override fun isEmpty() = storage.isEmpty()

    override fun iterator(): Iterator<CodePoint> = object : Iterator<CodePoint> {
        private var index = 0
        override fun hasNext() = index < storage.size
        override fun next() = if (index < storage.size) CodePoint(storage[index++]) else throw NoSuchElementException(index.toString())
    }

    override fun containsAll(elements: Collection<CodePoint>) = elements.all { storage.contains(it.value) }

    override fun contains(element: CodePoint): Boolean = storage.contains(element.value)

    operator fun get(index: Int) = CodePoint(storage[index])

    operator fun set(index: Int, value: CodePoint) {
        storage[index] = value.value
    }
}

inline fun CodePointArray(size: Int, init: (Int) -> CodePoint): CodePointArray {
    return CodePointArray(IntArray(size) { index -> (init(index)).value })
}

inline fun CodePointArray.filterMaybe(predicate: (CodePoint) -> Boolean): CodePointArray {
    val filtered = storage.filterTo(ArrayList(size)) { predicate(CodePoint(it)) }
    return CodePointArray(filtered.toIntArray())
}

fun CodePointArray.binarySearch(element: CodePoint, fromIndex: Int = 0, toIndex: Int = size): Int {
    return java.util.Arrays.binarySearch(storage, fromIndex, toIndex, element.value)
}