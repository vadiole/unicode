package vadiole.unicode

import android.content.Context

object ColorPrototype {
    val primaryScroller: Boolean get() = false

    fun scrollerSurface(context: Context): android.graphics.drawable.Drawable? = null

    fun wrap(context: Context): Context = context

    fun searchShadow(context: Context): android.graphics.drawable.Drawable? = null
}
