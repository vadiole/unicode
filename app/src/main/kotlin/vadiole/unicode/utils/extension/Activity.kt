package vadiole.unicode.utils.extension

import android.app.Activity
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

val Activity.insetsController: WindowInsetsControllerCompat
    get() = WindowCompat.getInsetsController(window, window.decorView)!!

val Configuration.isDarkMode: Boolean
    get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES