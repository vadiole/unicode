package vadiole.unicode.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.updateLayoutParams
import vadiole.unicode.R
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.common.IconButton
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.StateColorDrawable
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.TEXT_TITLE
import vadiole.unicode.ui.common.ToggleSwitch
import vadiole.unicode.ui.common.borderWidth
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.mono_regular
import vadiole.unicode.ui.common.mono_semibold
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick

class SettingsScreen(
    context: Context,
    private val userConfig: UserConfig,
    private val delegate: Delegate,
) : Screen(context) {
    interface Delegate {
        fun onBack()
        fun onShowUnsupportedCharsChanged()
        fun onDockEnabledChanged()
    }

    private val backButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_chevron_left)!!,
        floating = false,
    ).apply {
        setPadding(4.dp(context), 0, 4.dp(context), 0)
        contentDescription = context.getString(R.string.settings_back)
        onClick = { delegate.onBack() }
    }

    private val title = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_TITLE)
        typeface = mono_semibold
        includeFontPadding = false
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        setTextColor(context.getColor(R.color.windowTextPrimary))
        text = context.getString(R.string.settings_title)
    }

    private val header = FrameLayout(context).apply {
        setBackgroundColor(context.getColor(R.color.layer1))
        addView(backButton, frameParams(56.dp(context), matchParent, Gravity.LEFT))
        addView(title, frameParams(wrapContent, matchParent, Gravity.LEFT, marginLeft = 56.dp(context)))
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(header, linearParams(matchParent, 48.dp(context)))
        addView(View(context).apply {
            setBackgroundColor(context.getColor(R.color.floatingBorder))
        }, linearParams(matchParent, borderWidth(context).toInt()))
        addView(createDivider(), linearParams(matchParent, 1, marginTop = 16.dp(context)))
        addView(createGroup(), linearParams(matchParent, wrapContent))
        addView(createDivider(), linearParams(matchParent, 1))
    }

    private fun createGroup() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(context.getColor(R.color.layer1))
        addView(
            ToggleRow(context, context.getString(R.string.settings_dock), userConfig.dockEnabled) { checked ->
                userConfig.dockEnabled = checked
                delegate.onDockEnabledChanged()
            },
            linearParams(matchParent, wrapContent)
        )
        addView(createDivider(), linearParams(matchParent, 1, marginLeft = 16.dp(context)))
        addView(
            ToggleRow(context, context.getString(R.string.settings_show_unsupported), userConfig.showUnsupportedChars) { checked ->
                userConfig.showUnsupportedChars = checked
                delegate.onShowUnsupportedCharsChanged()
            },
            linearParams(matchParent, wrapContent)
        )
        addView(createDivider(), linearParams(matchParent, 1, marginLeft = 16.dp(context)))
        addView(
            ToggleRow(context, context.getString(R.string.settings_crash_reports), !userConfig.crashReportDisabled) { checked ->
                userConfig.crashReportDisabled = !checked
            },
            linearParams(matchParent, wrapContent)
        )
    }

    init {
        setBackgroundColor(context.getColor(R.color.layer0))
        addView(content, frameParams(matchParent, matchParent))
        setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.statusBars.top
            header.setPadding(0, top, 0, 0)
            header.updateLayoutParams { height = 48.dp(context) + top }
            setPadding(0, 0, 0, insets.navigationBars.bottom)
            insets
        }
    }

    private fun createDivider() = View(context).apply {
        setBackgroundColor(context.getColor(R.color.windowDivider))
    }

    private class ToggleRow(
        context: Context,
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ) : FrameLayout(context) {
        private val switch = ToggleSwitch(context).apply {
            setState(checked, animate = false)
            onToggle = onChanged
        }

        init {
            background = StateColorDrawable().apply {
                colors = ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_pressed),
                        intArrayOf(android.R.attr.state_pressed)
                    ),
                    intArrayOf(
                        context.getColor(R.color.layer1),
                        context.getColor(R.color.layer1Pressed),
                    ),
                )
            }
            minimumHeight = 56.dp(context)
            val labelView = TextView(context).apply {
                setPadding(0, 12.dp(context), 0, 12.dp(context))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
                typeface = mono_regular
                includeFontPadding = false
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                setTextColor(context.getColor(R.color.windowTextPrimary))
                text = label
            }
            addView(labelView, frameParams(matchParent, wrapContent, Gravity.LEFT or Gravity.CENTER_VERTICAL, marginLeft = 16.dp(context), marginRight = 72.dp(context)))
            addView(switch, frameParams(wrapContent, wrapContent, Gravity.RIGHT or Gravity.CENTER_VERTICAL, marginRight = 16.dp(context)))
            isClickable = true
            isFocusable = true
            onClick = { switch.toggle() }
        }
    }
}
