package vadiole.unicode.ui.common

import android.graphics.Typeface
import android.view.View

private val monoRegular: Typeface = Typeface.MONOSPACE
private val monoSemibold: Typeface = Typeface.create(Typeface.MONOSPACE, 600, false)

@Suppress("UnusedReceiverParameter")
val View.mono_regular: Typeface
    get() = monoRegular

@Suppress("UnusedReceiverParameter")
val View.mono_semibold: Typeface
    get() = monoSemibold
