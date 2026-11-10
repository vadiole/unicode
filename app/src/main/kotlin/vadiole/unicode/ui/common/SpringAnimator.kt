package vadiole.unicode.ui.common

import android.animation.ValueAnimator
import android.os.Build
import android.view.Choreographer

class SpringAnimator(
    stiffness: Float,
    dampingRatio: Float = 1f,
    minVisibleChange: Float = 1f,
    value: Float = 0f,
    onUpdate: UpdateListener? = null,
    onEnd: EndListener? = null,
) : Spring(stiffness, dampingRatio, minVisibleChange, value, onUpdate, onEnd), Choreographer.FrameCallback {

    override fun postFrame() = Choreographer.getInstance().postFrameCallback(this)

    override fun doFrame(frameTimeNanos: Long) {
        frame(frameTimeNanos, if (Build.VERSION.SDK_INT >= 26) ValueAnimator.getDurationScale() else 1f)
    }
}
