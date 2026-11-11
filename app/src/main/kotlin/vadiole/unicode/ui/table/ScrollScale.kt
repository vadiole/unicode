package vadiole.unicode.ui.table

import java.io.DataOutputStream
import java.nio.ByteBuffer
import vadiole.unicode.data.Block
import vadiole.unicode.data.UnicodeData
import kotlin.math.abs
import kotlin.math.sqrt

class ScrollScale private constructor(private val linear: FloatArray, private val thumb: FloatArray, private val speed: FloatArray) {
    fun toThumb(progress: Float) = map(linear, thumb, progress)

    fun toLinear(progress: Float) = map(thumb, linear, progress)

    fun offset(progress: Float, delta: Float): Float {
        var position = toLinear(progress)
        var remaining = delta
        var index = linear.binarySearch(position)
        if (index < 0) index = -index - 2
        index = index.coerceIn(0, speed.size - 1)
        while (remaining != 0f) {
            val forward = remaining > 0f
            val edge = if (forward) linear[index + 1] else linear[index]
            val reach = (edge - position) / speed[index]
            val isLast = if (forward) index == speed.size - 1 else index == 0
            if (isLast || abs(remaining) <= abs(reach)) {
                position += remaining * speed[index]
                break
            }
            position = edge
            remaining -= reach
            index += if (forward) 1 else -1
        }
        return toThumb(position.coerceIn(0f, 1f))
    }

    fun write(out: DataOutputStream) {
        out.writeInt(speed.size)
        for (array in arrayOf(linear, thumb, speed)) for (value in array) out.writeFloat(value)
    }

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
        private const val MAX_SPEED = 4f

        fun read(buffer: ByteBuffer): ScrollScale {
            val size = buffer.getInt()
            return ScrollScale(buffer.floats(size + 1), buffer.floats(size + 1), buffer.floats(size))
        }

        private fun ByteBuffer.floats(size: Int) = FloatArray(size) { getFloat() }

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
            val speed = FloatArray(blocks.size)
            var weight = 0f
            for (i in blocks.indices) {
                val size = first[i + 1] - first[i]
                val group = groups[i]
                val ratio = if (group >= 0 && groupSizes[group] > limit) groupSizes[group] / limit else 1f
                weight += size / ratio
                speed[i] = minOf(MAX_SPEED, sqrt(ratio))
                linear[i + 1] = first[i + 1].toFloat() / chars.size
                thumb[i + 1] = weight
            }
            for (i in thumb.indices) thumb[i] /= weight
            return ScrollScale(linear, thumb, speed)
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
