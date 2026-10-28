package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import vadiole.unicode.R

fun Context.rippleDrawable(content: Drawable, mask: Drawable? = null): RippleDrawable {
    return RippleDrawable(ColorStateList.valueOf(getColor(R.color.ripple)), content, mask)
}
