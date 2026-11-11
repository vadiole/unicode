package vadiole.unicode.ui.common

import android.os.Build
import android.view.View

val View.showsLayoutBounds: Boolean
    get() = Build.VERSION.SDK_INT >= 30 && isShowingLayoutBounds
