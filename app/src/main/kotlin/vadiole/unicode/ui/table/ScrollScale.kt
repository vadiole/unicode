package vadiole.unicode.ui.table

import vadiole.unicode.data.Block
import vadiole.unicode.data.UnicodeData

class ScrollScale private constructor(private val linear: FloatArray, private val thumb: FloatArray) {
    fun toThumb(progress: Float) = map(linear, thumb, progress)

    fun toLinear(progress: Float) = map(thumb, linear, progress)

    private fun map(from: FloatArray, to: FloatArray, value: Float): Float {
        if (value <= 0f) return 0f
        if (value >= 1f) return 1f
        var index = from.binarySearch(value)
        if (index < 0) index = -index - 2
        index = index.coerceIn(0, from.size - 2)
        val span = from[index + 1] - from[index]
        if (span <= 0f) return to[index]
        return to[index] + (value - from[index]) / span * (to[index + 1] - to[index])
    }

    companion object {
        private const val MAX_GROUP_RATIO = 1.5f

        fun create(chars: IntArray, blocks: Array<Block>, data: UnicodeData): ScrollScale? {
            if (chars.isEmpty() || blocks.isEmpty()) return null
            val first = IntArray(blocks.size + 1)
            for (i in 1 until blocks.size) {
                first[i] = chars.binarySearch(blocks[i].start).let { if (it < 0) -it - 1 else it }
            }
            first[blocks.size] = chars.size
            val groups = IntArray(blocks.size) { groupOf(blocks[it], data) }
            val groupSizes = IntArray(data.rangeCount)
            var largest = 0
            for (i in blocks.indices) {
                val size = first[i + 1] - first[i]
                if (groups[i] < 0) largest = maxOf(largest, size) else groupSizes[groups[i]] += size
            }
            val limit = largest * MAX_GROUP_RATIO
            if (limit <= 0f || groupSizes.none { it > limit }) return null
            val linear = FloatArray(blocks.size + 1)
            val thumb = FloatArray(blocks.size + 1)
            var weight = 0f
            for (i in blocks.indices) {
                val size = first[i + 1] - first[i]
                val group = groups[i]
                weight += if (group >= 0 && groupSizes[group] > limit) size * limit / groupSizes[group] else size.toFloat()
                linear[i + 1] = first[i + 1].toFloat() / chars.size
                thumb[i + 1] = weight
            }
            for (i in thumb.indices) thumb[i] /= weight
            return ScrollScale(linear, thumb)
        }

        private fun groupOf(block: Block, data: UnicodeData): Int {
            for (range in 0 until data.rangeCount) {
                if (data.rangeFirst[range] > block.end || data.rangeLast[range] < block.start) continue
                return data.rangePrefixes.indexOf(data.rangePrefixes[range])
            }
            return -1
        }
    }
}
