package vadiole.unicode.ui.common

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import vadiole.unicode.R

abstract class Screen(context: Context) : FrameLayout(context), CoroutineScope {
    private val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Toast.makeText(context, throwable.localizedMessage ?: context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
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
