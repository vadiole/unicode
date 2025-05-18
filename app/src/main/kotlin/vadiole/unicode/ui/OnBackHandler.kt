package vadiole.unicode.ui

import android.window.BackEvent
import kotlinx.coroutines.flow.StateFlow

interface OnBackHandler {

    val isBackEnabled: StateFlow<Boolean>

    fun onBackStarted(backEvent: BackEvent) = Unit

    fun onBackProgressed(backEvent: BackEvent) = Unit

    fun onBackCancelled() = Unit

    fun onBackInvoked()
}