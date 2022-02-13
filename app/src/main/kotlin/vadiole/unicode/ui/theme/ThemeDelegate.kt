package vadiole.unicode.ui.theme

import android.view.View

interface ThemeDelegate {
    fun applyTheme(theme: Theme)
    fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener)
    fun isAttachedToWindow(): Boolean
    fun invalidate()
}

