package vadiole.unicode.ui.common

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import vadiole.unicode.ui.extension.onClick

class ToggleCell(context: Context, label: String, checked: Boolean, onChanged: (Boolean) -> Unit) : FrameLayout(context) {
    val switch = ToggleSwitch(context).apply {
        setState(checked, animate = false)
        onToggle = onChanged
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val cell = ActionCell(context, label).apply {
        setPadding(paddingLeft, paddingTop, 84.dp(context), paddingBottom)
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Switch::class.java.name
                info.isCheckable = true
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    info.checked = if (switch.isChecked) android.view.accessibility.AccessibilityNodeInfo.CHECKED_STATE_TRUE
                    else android.view.accessibility.AccessibilityNodeInfo.CHECKED_STATE_FALSE
                } else {
                    @Suppress("DEPRECATION")
                    info.isChecked = switch.isChecked
                }
            }
        }
        onClick = { switch.toggle() }
    }

    init {
        addView(cell, frameParams(matchParent, wrapContent))
        addView(switch, frameParams(wrapContent, wrapContent, Gravity.RIGHT or Gravity.CENTER_VERTICAL, marginRight = 16.dp(context)))
    }
}
