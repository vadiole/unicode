package vadiole.unicode.ui.common

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

val WindowInsetsCompat.navigationBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.navigationBars())

val WindowInsetsCompat.statusBars: Insets
    get() = getInsets(WindowInsetsCompat.Type.statusBars())

val WindowInsetsCompat.systemBarsAndCutout: Insets
    get() = getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

val WindowInsetsCompat.ime: Insets
    get() = getInsets(WindowInsetsCompat.Type.ime())
