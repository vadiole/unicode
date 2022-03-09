package vadiole.unicode.utils.extension

import android.widget.TextView
import androidx.annotation.IntRange
import androidx.annotation.Px

fun TextView.setLineHeightX(
    @Px @IntRange(from = 0) lineHeight: Int
) {
    require(lineHeight >= 0)
    val fontHeight = paint.getFontMetricsInt(null)
    // Make sure we don't setLineSpacing if it's not needed to avoid unnecessary redraw.
    if (lineHeight != fontHeight) {
        // Set lineSpacingExtra by the difference of lineSpacing with lineHeight
        setLineSpacing((lineHeight - fontHeight).toFloat(), 1f)
    }
}