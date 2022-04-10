package vadiole.unicode.utils.extension

import android.view.View
import android.view.ViewGroup

inline fun ViewGroup.forEach(action: (view: View) -> Unit) {
    for (index in 0 until childCount) {
        action(getChildAt(index))
    }
}