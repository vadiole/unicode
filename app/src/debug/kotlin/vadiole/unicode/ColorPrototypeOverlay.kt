package vadiole.unicode

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import kotlin.math.abs
import vadiole.unicode.ui.common.dp

internal class ColorPrototypeOverlay(private val activity: Activity) {
    private val ink = 0xFFF5F5F7.toInt()
    private val muted = 0xFFB9B9C1.toInt()
    private val surface = 0xFF29292E.toInt()
    private val selected = 0xFFE5E5EA.toInt()
    private val root = FrameLayout(activity).apply { id = R.id.prototype_overlay }
    private val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(12).toFloat()
        background = shape(surface, 16)
        setPadding(dp(8), dp(4), dp(8), dp(8))
    }
    private val header = TextView(activity).apply {
        id = R.id.prototype_header
        setTextColor(ink)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        minimumHeight = dp(48)
        isClickable = true
        isFocusable = true
        background = ripple(surface, 12)
    }
    private val controls = ScrollView(activity).apply { isFillViewport = false }
    private var insetTop = 0
    private var insetBottom = 0

    fun attach(content: ViewGroup) {
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(body)
        panel.addView(header, LinearLayout.LayoutParams(-1, -2))
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2))
        body.addView(row(
            option(R.id.prototype_original, R.string.prototype_original, ColorPrototype.fixes.isEmpty()) {
                ColorPrototype.select(activity, emptySet())
            },
            option(R.id.prototype_fixed, R.string.prototype_fixed, ColorPrototype.fixes == ColorPrototype.allFixes) {
                ColorPrototype.select(activity, ColorPrototype.allFixes)
            },
        ))
        body.addView(row(
            option(R.id.prototype_system, R.string.prototype_system, ColorPrototype.nightMode == null) {
                ColorPrototype.select(activity, mode = null)
            },
            option(R.id.prototype_light, R.string.prototype_light, ColorPrototype.nightMode == Configuration.UI_MODE_NIGHT_NO) {
                ColorPrototype.select(activity, mode = Configuration.UI_MODE_NIGHT_NO)
            },
            option(R.id.prototype_dark, R.string.prototype_dark, ColorPrototype.nightMode == Configuration.UI_MODE_NIGHT_YES) {
                ColorPrototype.select(activity, mode = Configuration.UI_MODE_NIGHT_YES)
            },
        ))
        val ids = intArrayOf(R.id.prototype_abbreviations, R.id.prototype_scrollbar, R.id.prototype_secondary, R.id.prototype_accent, R.id.prototype_borders, R.id.prototype_search_shadow, R.id.prototype_search_shadow_light, R.id.prototype_scroller_surface, R.id.prototype_scroller_primary)
        for (fix in ColorPrototype.Fix.entries) {
            body.addView(CheckBox(activity).apply {
                id = ids[fix.ordinal]
                setText(fix.label)
                textSize = 14f
                setTextColor(ink)
                buttonTintList = ColorStateList.valueOf(muted)
                minimumHeight = dp(48)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                isChecked = fix in ColorPrototype.fixes
                setOnCheckedChangeListener { _, checked ->
                    var selected = if (checked) ColorPrototype.fixes + fix else ColorPrototype.fixes - fix
                    if (checked && fix == ColorPrototype.Fix.SCROLLER_SURFACE) selected = selected - ColorPrototype.Fix.SCROLLER_PRIMARY
                    if (checked && fix == ColorPrototype.Fix.SCROLLER_PRIMARY) selected = selected - ColorPrototype.Fix.SCROLLER_SURFACE
                    ColorPrototype.select(activity, selected)
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val accent = activity.getColor(R.color.colorPrimary)
        body.addView(TextView(activity).apply {
            text = activity.getString(R.string.prototype_sample, String.format("#%06X", accent and 0xFFFFFF))
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(dp(12), 0, dp(12), 0)
            val swatch = shape(accent, 4).apply { setBounds(0, 0, dp(24), dp(24)) }
            setCompoundDrawables(swatch, null, null, null)
            compoundDrawablePadding = dp(8)
        })
        root.addView(panel, FrameLayout.LayoutParams(dp(320), -2))
        content.addView(root, ViewGroup.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insetTop = bars.top
            insetBottom = bars.bottom
            position()
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) position()
        }
        header.setOnClickListener {
            ColorPrototype.expanded = !ColorPrototype.expanded
            update()
        }
        dragHeader()
        update()
        root.requestApplyInsets()
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) root.post { updateSystemBars() }
        }
        root.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        })
        root.post { updateSystemBars() }
    }

    private fun updateSystemBars() {
        val mode = ColorPrototype.nightMode ?: (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        val light = mode != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun update() {
        val variant = when {
            ColorPrototype.fixes.isEmpty() -> activity.getString(R.string.prototype_original)
            ColorPrototype.fixes == ColorPrototype.allFixes -> activity.getString(R.string.prototype_fixed)
            else -> activity.getString(R.string.prototype_custom, ColorPrototype.fixes.size)
        }
        header.text = activity.getString(R.string.prototype_title, variant, if (ColorPrototype.expanded) "▴" else "▾")
        header.contentDescription = "${header.text}. ${activity.getString(R.string.prototype_drag)}"
        controls.visibility = if (ColorPrototype.expanded) View.VISIBLE else View.GONE
        position()
    }

    private fun position() {
        if (root.width == 0 || root.height == 0) return
        val params = panel.layoutParams as FrameLayout.LayoutParams
        params.width = minOf(dp(if (ColorPrototype.expanded) 320 else 220), root.width - dp(24))
        controls.layoutParams = (controls.layoutParams as LinearLayout.LayoutParams).apply {
            height = -2
        }
        controls.measure(
            View.MeasureSpec.makeMeasureSpec((params.width - dp(16)).coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((root.height - insetTop - insetBottom - dp(88)).coerceAtLeast(0), View.MeasureSpec.AT_MOST),
        )
        if (ColorPrototype.expanded) controls.layoutParams.height = controls.measuredHeight
        val panelHeight = if (ColorPrototype.expanded) controls.measuredHeight + dp(60) else dp(60)
        val x = if (ColorPrototype.panelX < 0) dp(16) else ColorPrototype.panelX
        val y = if (ColorPrototype.panelY < 0) insetTop + dp(112) else ColorPrototype.panelY
        params.leftMargin = x.coerceIn(dp(8), (root.width - params.width - dp(8)).coerceAtLeast(dp(8)))
        params.topMargin = y.coerceIn(insetTop + dp(8), (root.height - insetBottom - panelHeight - dp(8)).coerceAtLeast(insetTop + dp(8)))
        panel.layoutParams = params
    }

    private fun dragHeader() {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        header.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    val params = panel.layoutParams as FrameLayout.LayoutParams
                    startX = params.leftMargin
                    startY = params.topMargin
                    dragging = false
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        ColorPrototype.panelX = startX + dx.toInt()
                        ColorPrototype.panelY = startY + dy.toInt()
                        position()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!dragging) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun option(id: Int, label: Int, active: Boolean, action: () -> Unit): Button = Button(activity).apply {
        this.id = id
        setText(label)
        isAllCaps = false
        setPadding(dp(4), dp(4), dp(4), dp(4))
        textSize = 14f
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setTextColor(if (active) Color.BLACK else ink)
        background = ripple(if (active) selected else 0xFF3B3B42.toInt(), 10)
        isSelected = active
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun row(vararg options: View): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        for (option in options) addView(option, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x336C6C70), shape(color, radius), shape(Color.WHITE, radius),
    )

    private fun dp(value: Int): Int = value.dp(activity)
}
