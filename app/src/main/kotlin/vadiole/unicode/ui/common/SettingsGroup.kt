package vadiole.unicode.ui.common

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import vadiole.unicode.R

class SettingsGroup(context: Context, items: List<View>) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        items.forEachIndexed { index, item ->
            if (index > 0) {
                addView(View(context).apply {
                    setBackgroundColor(context.getColor(R.color.windowDivider))
                }, linearParams(matchParent, 1))
            }
            val top = index == 0
            val bottom = index == items.lastIndex
            when (item) {
                is ActionCell -> item.setCorners(top, bottom)
                is ToggleCell -> item.cell.setCorners(top, bottom)
                is SliderCell -> item.setCorners(top, bottom)
            }
            addView(item, linearParams(matchParent, wrapContent))
        }
    }
}
