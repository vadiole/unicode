package vadiole.unicode.utils

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

val Activity.insetsController: WindowInsetsControllerCompat
    get() = WindowCompat.getInsetsController(window, window.decorView)!!

val Configuration.isDarkMode: Boolean
    get() = Build.VERSION.SDK_INT >= 30 && isNightModeActive