package vadiole.unicode.ui.theme

import android.view.View

class AppTheme(scheme: Scheme) : Theme() {
    override var colors: HashMap<String, Int> = scheme.colors
    private val observers = mutableSetOf<ThemeDelegate>()

    fun observe(observer: ThemeDelegate) {
        if (observer.isAttachedToWindow()) {
            observer.applyTheme(this)
        }
        observer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observers.add(observer)
                observer.applyTheme(this@AppTheme)
            }
            override fun onViewDetachedFromWindow(v: View) {
                observers.remove(observer)
            }
        })
    }

    fun applyScheme(value: Scheme) {
        colors = value.colors
        observers.forEach { observer ->
            observer.applyTheme(this)
        }
    }

    enum class Scheme(val colors: HashMap<String, Int>) {
        BLUE_DARK(
            hashMapOf(
                key_dialogBackground to 0xFF1D222A.toInt(),
                key_dialogTextPrimary to 0xFF_FFFFFF.toInt(),
                key_dialogIcon to 0xFF_FFFFFF.toInt(),
                key_dialogButton to 0xFF_64B5EF.toInt(),
                key_dialogButtonDanger to 0xFFD9635F.toInt(),
                key_dialogListRipple to 0x14_E5F2FF,
                key_dialogDim to 0xFF_000000.toInt(),
                key_windowBackground to 0xFF111318.toInt(),
                key_windowTextPrimary to 0xFF_FFFFFF.toInt(),
                key_windowDivider to 0xFF232933.toInt(),
                key_windowRipple to 0x14_E5F2FF,
                key_topBarBackground to 0xFF1D222A.toInt(),
                key_tabBarBackground to 0xFF1D222A.toInt(),
                key_tabBarItem to 0xFFB4C2D3.toInt(),
                key_tabBarItemSelected to 0xFF_64B5EF.toInt(),
            )
        ),
        BLUE_LIGHT(
            hashMapOf(
                key_dialogBackground to 0xFFFFFFFF.toInt(),
                key_dialogTextPrimary to 0xFF_000000.toInt(),
                key_dialogIcon to 0xFF_000000.toInt(),
                key_dialogButton to 0xFF3E76C6.toInt(),
                key_dialogButtonDanger to 0xFFEC5242.toInt(),
                key_dialogListRipple to 0x0F_000000,
                key_dialogDim to 0xFF_000000.toInt(),

                key_windowBackground to 0xFF_FFFFFF.toInt(),
                key_windowTextPrimary to 0xFF_000000.toInt(),
                key_windowDivider to 0xFFEAEAEA.toInt(),
                key_windowRipple to 0x0F_000000,
                key_topBarBackground to 0xFFFFFFFF.toInt(),
                key_tabBarBackground to 0xFF_FFFFFF.toInt(),
                key_tabBarItem to 0xFF9F9E9E.toInt(),
                key_tabBarItemSelected to 0xFF3E76C6.toInt(),
            )
        )
    }
}
