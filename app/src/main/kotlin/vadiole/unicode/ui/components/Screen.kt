package vadiole.unicode.ui.components

import android.content.Context
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

abstract class Screen(context: Context) : FrameLayout(context), CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate

    init {
        isMotionEventSplittingEnabled = false
        isClickable = true
        isFocusable = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancel()
    }
}