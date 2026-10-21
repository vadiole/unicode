package vadiole.unicode.ui.table.dock

import vadiole.unicode.data.CodePoint
import vadiole.unicode.data.config.UserConfig

class DockController(private val userConfig: UserConfig) {
    private val builder = StringBuilder()

    init {
        val raw = userConfig.collected
        if (raw.isNotEmpty()) {
            for (part in raw.split(',')) {
                val value = part.toIntOrNull() ?: continue
                if (Character.isValidCodePoint(value)) builder.appendCodePoint(value)
            }
        }
    }

    val text: String
        get() = builder.toString()

    val length: Int
        get() = builder.length

    var usedCollect: Boolean = userConfig.usedCollect
        private set

    var usedCopy: Boolean = userConfig.usedCopy
        private set

    private var deleted = ""

    fun add(codePoint: CodePoint) {
        deleted = ""
        builder.appendCodePoint(codePoint.value)
        save()
        if (!usedCollect) {
            usedCollect = true
            userConfig.usedCollect = true
        }
    }

    fun markCopied() {
        if (usedCopy) return
        usedCopy = true
        userConfig.usedCopy = true
    }

    fun backspace(): Boolean {
        if (builder.isEmpty()) return false
        if (deleted.isEmpty()) deleted = builder.toString()
        val last = Character.codePointBefore(builder, builder.length)
        builder.setLength(builder.length - Character.charCount(last))
        save()
        return true
    }

    fun clear(): Boolean {
        if (builder.isEmpty()) return false
        if (deleted.isEmpty()) deleted = builder.toString()
        builder.setLength(0)
        save()
        return true
    }

    fun restore(): Boolean {
        if (builder.isNotEmpty() || deleted.isEmpty()) return false
        builder.append(deleted)
        deleted = ""
        save()
        return true
    }

    private fun save() {
        val out = StringBuilder(builder.length * 6)
        var index = 0
        while (index < builder.length) {
            val codePoint = Character.codePointAt(builder, index)
            if (out.isNotEmpty()) out.append(',')
            out.append(codePoint)
            index += Character.charCount(codePoint)
        }
        userConfig.collected = out.toString()
    }
}
