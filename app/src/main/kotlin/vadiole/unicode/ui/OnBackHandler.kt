package vadiole.unicode.ui

import android.os.Build
import android.window.BackEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.StateFlow

interface OnBackHandler {

    val isBackEnabled: StateFlow<Boolean>

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onBackStarted(backEvent: BackEvent) = Unit

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onBackProgressed(backEvent: BackEvent) = Unit

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onBackCancelled() = Unit

    fun onBackInvoked()
}