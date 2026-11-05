package vadiole.unicode.ui.common

import android.content.Context
import android.widget.SeekBar
import kotlin.math.roundToInt

class Slider(context: Context, val range: ClosedFloatingPointRange<Float>) : SeekBar(context) {
    var onValueChanged: ((Float) -> Unit)? = null
    var onInteractionChanged: ((Boolean) -> Unit)? = null

    var value: Float
        get() = range.start + (range.endInclusive - range.start) * progress / max
        set(value) {
            progress = ((value.coerceIn(range) - range.start) / (range.endInclusive - range.start) * max).roundToInt()
        }

    init {
        require(range.endInclusive > range.start)
        max = 1000
        minimumHeight = 48.dp(context)
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onValueChanged?.invoke(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                parent.requestDisallowInterceptTouchEvent(true)
                onInteractionChanged?.invoke(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                parent.requestDisallowInterceptTouchEvent(false)
                onInteractionChanged?.invoke(false)
            }
        })
    }
}
