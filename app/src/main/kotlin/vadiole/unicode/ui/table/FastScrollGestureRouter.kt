package vadiole.unicode.ui.table

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

internal class FastScrollGestureRouter(host: View) {
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val location = IntArray(2)
    private var downEvent: MotionEvent? = null
    private var target: FastScrollView? = null

    fun onInterceptTouchEvent(event: MotionEvent, scroller: FastScrollView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                if (!scroller.isShown) return false
                scroller.getLocationOnScreen(location)
                if (scroller.isInDragZone(event.rawX - location[0], event.rawY - location[1])) {
                    downEvent = MotionEvent.obtain(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val down = downEvent ?: return false
                if (!scroller.isShown) {
                    cancel()
                    return false
                }
                val dx = event.rawX - down.rawX
                val dy = event.rawY - down.rawY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    val handled = forward(down, scroller)
                    down.recycle()
                    downEvent = null
                    if (handled) {
                        target = scroller
                        forward(event, scroller)
                    }
                    return handled
                }
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }
        return false
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val scroller = target ?: return false
        forward(event, scroller)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            target = null
        }
        return true
    }

    fun cancel() {
        downEvent?.recycle()
        downEvent = null
        target?.let { scroller ->
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            scroller.onTouchEvent(cancel)
            cancel.recycle()
        }
        target = null
    }

    private fun forward(event: MotionEvent, scroller: FastScrollView): Boolean {
        scroller.getLocationOnScreen(location)
        val local = MotionEvent.obtain(event)
        local.setLocation(event.rawX - location[0], event.rawY - location[1])
        val handled = scroller.onTouchEvent(local)
        local.recycle()
        return handled
    }
}
