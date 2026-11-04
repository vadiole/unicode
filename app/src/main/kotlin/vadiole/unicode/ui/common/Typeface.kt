package vadiole.unicode.ui.common

import android.graphics.Typeface
import android.os.Build
import android.view.View

private val fontRegular: Typeface = Typeface.DEFAULT
private val fontSemibold: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Typeface.create(Typeface.DEFAULT, 600, false)
} else {
    Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

@Suppress("UnusedReceiverParameter")
val View.font_regular: Typeface
    get() = fontRegular

@Suppress("UnusedReceiverParameter")
val View.font_semibold: Typeface
    get() = fontSemibold

const val TEXT_TITLE = 17f
const val TEXT_BODY = 15f
const val TEXT_SECONDARY = 13f
const val TEXT_CAPTION = 11f
