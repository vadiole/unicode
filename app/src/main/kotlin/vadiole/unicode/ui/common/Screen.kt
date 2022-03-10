package vadiole.unicode.ui.common

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

abstract class Screen(context: Context) : FrameLayout(context), CoroutineScope {
    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Toast.makeText(context, throwable.localizedMessage ?: "Unknown error occurred", Toast.LENGTH_SHORT).show()
        Log.e(coroutineContext.toString(), throwable.message ?: "unknown error", throwable)
    }
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler

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