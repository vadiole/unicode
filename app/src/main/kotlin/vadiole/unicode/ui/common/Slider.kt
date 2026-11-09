package vadiole.unicode.ui.common

import android.content.Context
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

class Slider(context: Context, val range: ClosedFloatingPointRange<Float>) : SeekBar(context) {
    var onValueChanged: ((Float) -> Unit)? = null
    var onInteractionChanged: ((Boolean) -> Unit)? = null
    var detent = Float.NaN
        set(value) {
            field = value
            detentProgress = if (value.isNaN()) -1 else toProgress(value)
        }
    private var detentProgress = -1
    private val haptic = TickHaptic(this, 0.8f)
    private var atDetent = false

    var value: Float
        get() = range.start + (range.endInclusive - range.start) * progress / max
        set(value) {
            progress = toProgress(value)
        }

    init {
        require(range.endInclusive > range.start)
        max = 1000
        minimumHeight = 48.dp(context)
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val snapped = snap(progress)
                if (snapped != progress) setProgress(snapped)
                val reached = detentProgress >= 0 && snapped == detentProgress
                if (reached && !atDetent) haptic.perform()
                atDetent = reached
                onValueChanged?.invoke(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                atDetent = detentProgress >= 0 && progress == detentProgress
                onInteractionChanged?.invoke(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onInteractionChanged?.invoke(false)
            }
        })
    }

    private fun toProgress(value: Float) =
        ((value.coerceIn(range) - range.start) / (range.endInclusive - range.start) * max).roundToInt()

    private fun snap(progress: Int) =
        if (detentProgress >= 0 && abs(progress - detentProgress) <= max / 60) detentProgress else progress
}
