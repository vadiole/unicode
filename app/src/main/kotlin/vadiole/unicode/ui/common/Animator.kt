package vadiole.unicode.ui.common

import android.animation.ValueAnimator
import android.os.Build

val animatorsEnabled: Boolean
    get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

val animatorDurationScale: Float
    get() = if (Build.VERSION.SDK_INT >= 33) ValueAnimator.getDurationScale() else 1f
