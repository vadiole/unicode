package vadiole.unicode.ui.common

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

// Android spring animation wrapper.
class Spring(
    private val stiffness: Float,
    private val dampingRatio: Float,
    private val onUpdated: (Float) -> Unit,
    private val onEnd: (Float) -> Unit,
) {
    private var internalAnimation: SpringAnimation? = null

    val isRunning: Boolean
        get() = internalAnimation?.isRunning ?: false

    fun start(
        position: Float,
        velocity: Float = 0f,
    ) {
        internalAnimation?.cancel()
        internalAnimation = SpringAnimation(FloatValueHolder(0f), 0f).apply {
            spring = SpringForce(position).apply {
                stiffness = this@Spring.stiffness
                dampingRatio = this@Spring.dampingRatio
            }
            setStartVelocity(velocity)
            addUpdateListener { _, value, _ ->
                onUpdated(value)
            }
            addEndListener { _, canceled, value, _ ->
                if (!canceled) {
                    onEnd(value)
                }
            }
            start()
        }
    }

    fun cancel() {
        internalAnimation?.cancel()
        internalAnimation = null
    }
}