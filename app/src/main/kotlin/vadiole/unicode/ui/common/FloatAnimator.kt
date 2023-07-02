package vadiole.unicode.ui.common

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class FloatAnimator(
    initialValue: Float,
    private val stiffness: Float = SpringForce.STIFFNESS_LOW,
    private val dampingRatio: Float = SpringForce.DAMPING_RATIO_NO_BOUNCY,
) {
    private val floatValueHolder = FloatValueHolder(initialValue)
    private val springAnimation = SpringAnimation(floatValueHolder)

    fun onUpdate(callback: (value: Float) -> Unit): FloatAnimator {
        springAnimation.addUpdateListener { _, value, _ ->
            callback(value)
        }
        return this
    }

    fun setValue(value: Float) {
        springAnimation.apply {
            cancel()
            spring = SpringForce(value).apply {
                this.stiffness = this@FloatAnimator.stiffness
                this.dampingRatio = this@FloatAnimator.dampingRatio
            }
            start()
        }
    }
}
