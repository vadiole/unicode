package vadiole.unicode.ui.theme

import android.view.View

class AppTheme(scheme: Scheme) : Theme() {
    override var colors: HashMap<String, Int> = scheme.colors
    private val observers = mutableSetOf<ThemeDelegate>()

    init {
        sharedDividerPaint.color = getColor(key_windowDivider)
    }

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

        sharedDividerPaint.color = getColor(key_windowDivider)

        observers.forEach { observer ->
            observer.applyTheme(this)
        }
    }

    enum class Scheme(val colors: HashMap<String, Int>) {
        BLUE_DARK(
            hashMapOf(
                key_dialogBackground to 0xFF1C1D1E.toInt(),
                key_dialogSurface to 0xFF_2D2E2F.toInt(),
                key_dialogSurfacePressed to 0xFF_3E3E3F.toInt(),
                key_dialogIcon to 0xFF_FFFFFF.toInt(),
                key_dialogButton to 0xFF_64B5EF.toInt(),
                key_dialogButtonDanger to 0xFF_D9635F.toInt(),
                key_dialogListRipple to 0x14_E5F2FF,
                key_dialogDim to 0xFF_000000.toInt(),
                key_windowBackground to 0xFF_1C1D1E.toInt(),
                key_windowTextPrimary to 0xFF_FFFFFF.toInt(),
                key_windowTextSecondary to 0xFF_989899.toInt(),
                key_windowTextAccent to 0xFF_55A4F8.toInt(),
                key_windowDivider to 0x53_545558,
                key_windowRipple to 0x14_E5F2FF,
                key_topBarBackground to 0xFF_1E1E1F.toInt(),
                key_tabBarBackground to 0xFF_1E1E1F.toInt(),
                key_tabBarItem to 0xFF_B5C2D3.toInt(),
                key_tabBarItemSelected to 0xFF_55A4F8.toInt(),
            )
        ),


        BLUE_LIGHT(
            hashMapOf(
                key_dialogBackground to 0xFF_F8F8F9.toInt(),
                key_dialogSurface to 0xFF_FFFFFF.toInt(),
                key_dialogSurfacePressed to 0xFF_E5E5E7.toInt(),
                key_dialogIcon to 0xFF_000000.toInt(),
                key_dialogButton to 0xFF_3E76C6.toInt(),
                key_dialogButtonDanger to 0xFF_EC5242.toInt(),
                key_dialogListRipple to 0x0F_000000,
                key_dialogDim to 0xFF_000000.toInt(),
                key_windowBackground to 0xFF_FFFFFF.toInt(),
                key_windowTextPrimary to 0xFF_000000.toInt(),
                key_windowTextSecondary to 0xFF_88888C.toInt(),
                key_windowTextAccent to 0xFF_417FC6.toInt(),
                key_windowDivider to 0x2E_3C3C43,
                key_windowRipple to 0x0F_000000,
                key_topBarBackground to 0xFF_FFFFFF.toInt(),
                key_tabBarBackground to 0xFF_FFFFFF.toInt(),
                key_tabBarItem to 0xFF_9F9E9E.toInt(),
                key_tabBarItemSelected to 0xFF_417FC6.toInt(),
            )
        );
    }
}