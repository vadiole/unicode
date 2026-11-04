package vadiole.unicode.ui.common

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

class TickHaptic(private val view: View, intensity: Float) {
    private val vibrator = if (Build.VERSION.SDK_INT >= 33) {
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else null
    private val effect = if (Build.VERSION.SDK_INT >= 33 &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
    ) {
        VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)
            .compose()
    } else null
    private val attributes = if (Build.VERSION.SDK_INT >= 33) {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    } else null

    fun perform() {
        if (Build.VERSION.SDK_INT >= 33 && effect != null && attributes != null) {
            vibrator?.vibrate(effect, attributes)
        } else {
            val constant = if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }
    }
}
