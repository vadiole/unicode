package vadiole.unicode.ui.theme

import android.graphics.Typeface
import android.view.View

private const val semiboldPath = "font/roboto_semibold.otf"
val View.roboto_semibold: Typeface
    get() = Typeface.createFromAsset(resources.assets, semiboldPath)

private const val regularPath = "font/roboto_regular.otf"
val View.roboto_regular: Typeface
    get() = Typeface.createFromAsset(resources.assets, regularPath)