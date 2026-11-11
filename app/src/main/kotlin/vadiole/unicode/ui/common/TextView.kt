package vadiole.unicode.ui.common

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.annotation.Px

fun TextView.setLineHeightCompat(
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

fun EditText.setCursorDrawable(drawable: Drawable) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        textCursorDrawable = drawable
    } else {
        try {
            @SuppressLint("BlockedPrivateApi")
            val field = TextView::class.java.getDeclaredField("mCursorDrawable")
            field.isAccessible = true
            field.set(this, drawable)
        } catch (throwable: Throwable) {
            Log.e("EditText", "Reflection", throwable)
        }
    }
}
