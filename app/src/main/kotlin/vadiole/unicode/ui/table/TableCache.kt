package vadiole.unicode.ui.table

import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import vadiole.unicode.data.Block
import vadiole.unicode.data.BlockList
import vadiole.unicode.data.CodePoint

class TableCache(
    val chars: IntArray,
    val abbreviations: Map<CodePoint, String>,
    val blockList: BlockList,
    val scrollScale: ScrollScale?,
) {
    fun write(file: File, key: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            val checksum = CRC32()
            DataOutputStream(CheckedOutputStream(temp.outputStream(), checksum).buffered(1 shl 16)).use { out ->
                out.writeString(key)
                out.writeInts(chars)
                out.writeInt(abbreviations.size)
                for ((codePoint, text) in abbreviations) {
                    out.writeInt(codePoint.value)
                    out.writeString(text)
                }
                val blocks = blockList.blocks
                out.writeInt(blocks.size)
                for (block in blocks) {
                    out.writeInt(block.id)
                    out.writeInt(block.start)
                    out.writeInt(block.end)
                    out.writeString(block.name)
                }
                out.writeInts(blockList.icons)
                out.write(blockList.iconFlags)
                out.write(blockList.types)
                for (label in blockList.labels) out.writeString(label)
                out.writeBoolean(scrollScale != null)
                scrollScale?.write(out)
                out.flush()
                out.writeLong(checksum.value)
            }
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: IOException) {
            temp.delete()
        }
    }

    private fun DataOutputStream.writeString(value: String?) {
        if (value == null) return writeInt(-1)
        val bytes = value.toByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeInts(values: IntArray) {
        writeInt(values.size)
        write(ByteBuffer.allocate(values.size * 4).apply { asIntBuffer().put(values) }.array())
    }

    companion object {
        fun read(file: File, key: String): TableCache? = try {
            if (file.exists()) read(file.readBytes(), key) else null
        } catch (e: Exception) {
            null
        }

        private fun read(bytes: ByteArray, key: String): TableCache? {
            if (bytes.size < 8) return null
            val checksum = CRC32()
            checksum.update(bytes, 0, bytes.size - 8)
            val buffer = ByteBuffer.wrap(bytes, 0, bytes.size - 8)
            if (ByteBuffer.wrap(bytes).getLong(bytes.size - 8) != checksum.value) return null
            if (buffer.string() != key) return null
            val chars = buffer.ints()
            val abbreviations = HashMap<CodePoint, String>()
            repeat(buffer.getInt()) {
                abbreviations[CodePoint(buffer.getInt())] = buffer.string()!!
            }
            val blocks = Array(buffer.getInt()) {
                Block(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.string()!!)
            }
            val icons = buffer.ints()
            val iconFlags = ByteArray(icons.size).also(buffer::get)
            val types = ByteArray(blocks.size).also(buffer::get)
            val labels = Array(blocks.size) { buffer.string() }
            val scrollScale = if (buffer.get().toInt() != 0) ScrollScale.read(buffer) else null
            if (buffer.hasRemaining()) return null
            return TableCache(chars, abbreviations, BlockList(blocks, icons, iconFlags, types, labels), scrollScale)
        }

        private fun ByteBuffer.string(): String? {
            val size = getInt()
            if (size < 0) return null
            val value = String(array(), arrayOffset() + position(), size, Charsets.UTF_8)
            position(position() + size)
            return value
        }

        private fun ByteBuffer.ints(): IntArray {
            val values = IntArray(getInt())
            asIntBuffer().get(values)
            position(position() + values.size * 4)
            return values
        }
    }
}
