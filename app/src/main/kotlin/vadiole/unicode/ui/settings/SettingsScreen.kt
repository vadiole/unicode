package vadiole.unicode.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.widget.LinearLayout
import android.widget.Toast
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.recentRepository
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.ui.common.ActionCell
import vadiole.unicode.ui.common.PathRevealLayout
import vadiole.unicode.ui.common.SliderCell
import vadiole.unicode.ui.common.SquircleDrawable
import vadiole.unicode.ui.common.addSquircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.HoldActionCell
import vadiole.unicode.ui.common.ToggleCell
import vadiole.unicode.ui.common.SettingsTopBar
import vadiole.unicode.ui.common.SettingsGroup
import vadiole.unicode.ui.common.SettingsList
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
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
        fun onCharacterScaleChanged(value: Float)
        fun onTableRevealChanged(revealed: Boolean)
        fun onAbout()
    }

    private val revealLayout = PathRevealLayout(context).apply {
        setBackgroundColor(context.getColor(R.color.layer0))
        onRevealChanged = delegate::onTableRevealChanged
    }
    private var adjustingSize = false
    private val characterSizeCell = SliderCell(
        context,
        context.getString(R.string.settings_character_size_in_table),
        0.75f..2f,
        userConfig.tableCharacterScale,
    ).apply {
        slider.detent = 1f
        slider.onValueChanged = { value ->
            setValue(value)
            delegate.onCharacterScaleChanged(value)
            if (!adjustingSize) userConfig.tableCharacterScale = value
        }
        slider.onInteractionChanged = { active ->
            adjustingSize = active
            if (!active) userConfig.tableCharacterScale = slider.value
            setAppearanceInteraction(active)
        }
    }
    private val backgroundTintCell = SliderCell(
        context,
        context.getString(R.string.settings_background_tint),
        0f..1f,
        revealLayout.backgroundTintOpacity,
    ).apply {
        slider.onValueChanged = { value ->
            setValue(value)
            revealLayout.backgroundTintOpacity = value
        }
        slider.onInteractionChanged = ::setAppearanceInteraction
    }
    private val appearanceGroup = SettingsGroup(context, listOf(characterSizeCell, backgroundTintCell)).apply {
        background = SquircleDrawable(13.dp(context)).apply {
            colors = ColorStateList.valueOf(context.getColor(R.color.layer1))
        }
        characterSizeCell.background = null
        backgroundTintCell.background = null
    }

    private val clearRecentsCell = HoldActionCell(context, context.getString(R.string.settings_clear_recents)) {
        recentRepository.clear()
        Toast.makeText(context, R.string.toast_recents_cleared, Toast.LENGTH_SHORT).show()
    }.apply {
        isEnabled = userConfig.recentsEnabled
    }

    private val list = SettingsList(context).apply {
        add(SettingsGroup(context, buildList {
            add(ToggleCell(context, context.getString(R.string.settings_dock), userConfig.dockEnabled) { checked ->
                userConfig.dockEnabled = checked
                delegate.onDockEnabledChanged()
            })
            if (userConfig.topSearchEligible) {
                add(ToggleCell(context, context.getString(R.string.settings_search_at_top), userConfig.searchAtTop) { checked ->
                    userConfig.searchAtTop = checked
                })
            }
            add(ToggleCell(context, context.getString(R.string.settings_show_unsupported), userConfig.showUnsupportedChars) { checked ->
                userConfig.showUnsupportedChars = checked
                delegate.onShowUnsupportedCharsChanged()
            })
        }))
        add(appearanceGroup)
        add(SettingsGroup(context, listOf(
            ToggleCell(context, context.getString(R.string.settings_save_recents), userConfig.recentsEnabled) { checked ->
                userConfig.recentsEnabled = checked
                clearRecentsCell.isEnabled = checked
            },
            clearRecentsCell,
        )))
        add(SettingsGroup(context, listOf(
            ToggleCell(context, context.getString(R.string.settings_crash_reports), !userConfig.crashReportDisabled) { checked ->
                userConfig.crashReportDisabled = !checked
            },
            ActionCell(context, context.getString(R.string.about_title)).apply {
                setChevron()
                onClick = { delegate.onAbout() }
            },
        )))
    }

    init {
        addView(revealLayout, frameParams(matchParent, matchParent))
        revealLayout.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(SettingsTopBar(context, context.getString(R.string.settings_title)) { delegate.onBack() }, linearParams(matchParent, wrapContent))
            addView(list, linearParams(matchParent, 0, weight = 1f))
        }, frameParams(matchParent, matchParent))
    }

    private fun setAppearanceInteraction(active: Boolean) {
        if (active) {
            val bounds = Rect(0, 0, appearanceGroup.width, appearanceGroup.height)
            revealLayout.offsetDescendantRectToMyCoords(appearanceGroup, bounds)
            val radius = 13f.dp(context)
            val path = Path().apply {
                addSquircle(RectF(bounds), radius)
            }
            revealLayout.reveal(path)
        } else {
            revealLayout.restore()
        }
    }

    fun stopTableReveal() {
        userConfig.tableCharacterScale = characterSizeCell.slider.value
        revealLayout.reset()
    }

    override fun onDetachedFromWindow() {
        stopTableReveal()
        super.onDetachedFromWindow()
    }
}
