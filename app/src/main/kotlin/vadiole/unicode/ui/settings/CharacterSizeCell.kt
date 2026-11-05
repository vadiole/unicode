package vadiole.unicode.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.widget.LinearLayout
import kotlin.math.roundToInt
import vadiole.unicode.R
import vadiole.unicode.ui.common.ActionCell
import vadiole.unicode.ui.common.Slider
import vadiole.unicode.ui.common.SquircleDrawable
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.wrapContent

class CharacterSizeCell(context: Context, initialValue: Float) : LinearLayout(context) {
    val slider = Slider(context, 0.75f..2f)
    private val label = ActionCell(context, context.getString(R.string.settings_character_size_in_table)).apply {
        background = null
        isClickable = false
        isFocusable = false
    }

    init {
        orientation = VERTICAL
        background = SquircleDrawable(13.dp(context)).apply {
            colors = ColorStateList.valueOf(context.getColor(R.color.layer1))
        }
        addView(label, linearParams(matchParent, wrapContent))
        addView(slider, linearParams(matchParent, 48.dp(context), marginLeft = 16.dp(context), marginRight = 16.dp(context), marginBottom = 8.dp(context)))
        slider.contentDescription = context.getString(R.string.settings_character_size_in_table)
        slider.value = initialValue
        setValue(initialValue)
    }

    fun setValue(value: Float) {
        label.value = "${(value * 100).roundToInt()}%"
    }
}
