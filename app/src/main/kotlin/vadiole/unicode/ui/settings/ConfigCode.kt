package vadiole.unicode.ui.settings

import vadiole.unicode.data.config.UserConfig

object ConfigCode {
    private const val VERSION = 1
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(config: UserConfig): String {
        val flags = booleanArrayOf(
            config.dockEnabled,
            config.topSearchEligible,
            config.searchAtTop,
            config.topSearchEverEnabled,
            config.showUnsupportedChars,
            config.recentsEnabled,
            !config.crashReportDisabled,
            config.mergedDetailsActions,
            config.centerTitle,
            config.usedCollect,
            config.usedCopy,
            config.usedFastScroll,
            config.usedPinchToZoom,
        )
        var bits = 0
        flags.forEachIndexed { index, flag -> if (flag) bits = bits or (1 shl index) }
        val payload = (VERSION shl 15) or bits
        val check = payload % 31
        val value = (payload shl 5) or check
        val chars = CharArray(5)
        for (i in 4 downTo 0) chars[4 - i] = ALPHABET[(value ushr (i * 5)) and 31]
        return String(chars)
    }
}
