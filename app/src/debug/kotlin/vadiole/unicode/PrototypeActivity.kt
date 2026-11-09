package vadiole.unicode

import android.app.Activity
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowCompat.setDecorFitsSystemWindows
import androidx.core.view.updatePadding
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0
import vadiole.unicode.ui.common.SettingsGroup
import vadiole.unicode.ui.common.Slider
import vadiole.unicode.ui.common.SliderCell
import vadiole.unicode.ui.common.TEXT_BODY
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.ToggleCell
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.font_regular
import vadiole.unicode.ui.common.font_semibold
import vadiole.unicode.ui.common.insetsController
import vadiole.unicode.ui.common.isDarkMode
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.statusBars
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick

class PrototypeActivity : Activity() {
    private val margin by lazy { 16.dp(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = getColor(R.color.navigationBarScrim)
        }
        setContentView(content())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) return
        val dark = resources.configuration.isDarkMode
        insetsController.isAppearanceLightStatusBars = !dark
        insetsController.isAppearanceLightNavigationBars = !dark
    }

    private fun content(): View {
        val cell = SliderCell(this, "Character size", 0.75f..2f, 1f)
        val slider = cell.slider
        val preview = text("A ж ✓ 😀 →", TEXT_BODY * 2f, R.color.windowTextPrimary).apply {
            gravity = Gravity.CENTER
        }
        val status = text("idle", TEXT_SECONDARY, R.color.windowTextSecondary)
        slider.detent = 1f
        slider.onValueChanged = { value ->
            cell.setValue(value)
            preview.textSize = TEXT_BODY * 2f * value
        }
        slider.onInteractionChanged = { active -> status.text = if (active) "interacting" else "idle" }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text("Slider", TEXT_BODY, R.color.windowTextPrimary).apply { typeface = font_semibold }, linearParams(0, wrapContent, weight = 1f))
            addView(status, linearParams(wrapContent, wrapContent, marginRight = margin))
            addView(text("Reset", TEXT_BODY, R.color.colorPrimary).apply {
                gravity = Gravity.CENTER
                minWidth = 64.dp(context)
                onClick = { recreate() }
            }, linearParams(wrapContent, 48.dp(this@PrototypeActivity)))
        }

        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(panel)
        }
        tuning(cell, slider).forEach { (title, rows) ->
            panel.addView(text(title, TEXT_SECONDARY, R.color.windowTextSecondary), linearParams(matchParent, wrapContent, marginLeft = margin * 2, marginTop = margin, marginRight = margin, marginBottom = 6.dp(this)))
            panel.addView(group(rows), linearParams(matchParent, wrapContent, marginLeft = margin, marginRight = margin))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.layer0))
            addView(header, linearParams(matchParent, wrapContent, marginLeft = margin))
            addView(preview, linearParams(matchParent, 88.dp(context)))
            addView(SettingsGroup(context, listOf(cell)), linearParams(matchParent, wrapContent, marginLeft = margin, marginRight = margin))
            addView(View(context).apply { setBackgroundColor(getColor(R.color.windowDivider)) }, linearParams(matchParent, 1, marginTop = margin))
            addView(scroll, linearParams(matchParent, 0, weight = 1f))
            setOnApplyWindowInsetsListener(this) { view, insets ->
                view.updatePadding(top = insets.statusBars.top)
                scroll.updatePadding(bottom = margin + insets.navigationBars.bottom)
                insets
            }
        }
    }

    private fun tuning(cell: SliderCell, slider: Slider): List<Pair<String, List<View>>> = listOf(
        "Track" to listOf(
            dp("Height", slider::trackHeight, 2f, 24f, 0.5f),
            dp("Inner radius", slider::trackInnerRadius, 0f, 8f, 0.5f),
            dp("Gap", slider::gap, 0f, 8f, 0.5f),
            toggle("Accent active color", false) { accent ->
                slider.activeColor = getColor(if (accent) R.color.colorPrimary else R.color.primaryControl)
            },
        ),
        "Capsule" to listOf(
            dp("Width", slider::capsuleWidth, 12f, 64f, 1f),
            dp("Height", slider::capsuleHeight, 8f, 40f, 1f),
            number("Pressed scale", slider::pressedScale, 1f, 2f, 0.05f),
            dp("Border width", slider::borderWidth, 0f, 4f, 0.25f),
            number("Pressed fill alpha", slider::pressedFillAlpha, 0f, 1f, 0.05f),
        ),
        "Line" to listOf(
            dp("Width", slider::lineWidth, 0f, 6f, 0.25f),
            dp("Width pressed", slider::lineWidthPressed, 0f, 6f, 0.25f),
            dp("Height", slider::lineHeight, 0f, 30f, 1f),
            dp("Pressed inset", slider::linePressedInset, 0f, 12f, 0.5f),
            toggle("Color change on press", slider::lineColorChange),
        ),
        "Spring" to listOf(
            number("Press stiffness", slider::pressStiffness, 50f, 2000f, 50f),
            number("Press damping", slider::pressDamping, 0.1f, 1f, 0.05f),
            number("Tap stiffness", slider::tapStiffness, 50f, 2000f, 50f),
        ),
        "Label" to listOf(
            number("Pressed scale", cell::labelPressedScale, 1f, 1.5f, 0.01f),
            number("Stiffness", cell::labelStiffness, 50f, 2000f, 50f),
            number("Damping", cell::labelDamping, 0.1f, 1f, 0.05f),
            toggle("Color change on press", cell::labelColorChange),
        ),
        "Hum" to listOf(
            toggle("Hum", slider::hum),
            toggle("Per % step (off: movement)", slider::humPerStep),
            choice("Step size", listOf(1, 2, 5), { "$it%" }, slider::humStepPercent),
            param("Quiet after click, ms", 0f, 300f, 10f, { slider.humQuietAfterClickMs.toFloat() }, { slider.humQuietAfterClickMs = it.toLong() }),
            param("Min interval, ms", 0f, 150f, 5f, { slider.humIntervalMs.toFloat() }, { slider.humIntervalMs = it.toLong() }),
            number("Min amplitude", slider::humMinAmplitude, 0f, 1f, 0.01f),
            number("Max amplitude", slider::humMaxAmplitude, 0f, 1f, 0.01f),
            param("Full speed, dp/s", 100f, 5000f, 100f, { slider.humFullSpeed / density }, { slider.humFullSpeed = it * density }),
            number("Speed curve", slider::humSpeedCurve, 0.2f, 4f, 0.1f),
            param("Movement step, % of range", 0f, 5f, 0.1f, { slider.humStep * 100f }, { slider.humStep = it / 100f }),
            toggle("Force fallback", slider::humForceFallback),
        ),
        "Clicks" to listOf(
            toggle("End clicks", slider::endClicks),
            toggle("Detent click", slider::detentClick),
            choice("Effect", listOf(Slider.CLICK, Slider.LOW_TICK, Slider.THRESHOLD, Slider.TICK), { clickEffects[it] }, slider::clickEffect),
            number("Strength", slider::clickStrength, 0f, 1f, 0.05f),
            param("Detent band, % of range", 0f, 20f, 0.5f, { slider.detentBand * 100f }, { slider.detentBand = it / 100f }),
        ),
        "Touch" to listOf(
            toggle("Tap to jump", slider::tapToJump),
            toggle("Jump on track drag", slider::jumpOnTrackDrag),
            number("Slop multiplier", slider::slopMultiplier, 0.5f, 4f, 0.25f),
        ),
    )

    private val density get() = resources.displayMetrics.density
    private val clickEffects = listOf("click", "low tick", "threshold", "tick")

    private fun choice(label: String, options: List<Int>, name: (Int) -> String, property: KMutableProperty0<Int>): View {
        val value = text(name(property.get()), TEXT_BODY, R.color.windowTextSecondary)
        val seekBar = SeekBar(this).apply {
            max = options.lastIndex
            progress = options.indexOf(property.get()).coerceAtLeast(0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    property.set(options[progress])
                    value.text = name(options[progress])
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        return row(label, value, seekBar)
    }

    private fun dp(label: String, property: KMutableProperty0<Float>, min: Float, max: Float, step: Float) =
        param(label, min, max, step, { property.get() / density }, { property.set(it * density) })

    private fun number(label: String, property: KMutableProperty0<Float>, min: Float, max: Float, step: Float) =
        param(label, min, max, step, property::get, property::set)

    private fun toggle(label: String, property: KMutableProperty0<Boolean>) = toggle(label, property.get(), property::set)

    private fun toggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) = ToggleCell(this, label, checked, onChanged).apply {
        cell.setCorners(top = false, bottom = false)
    }

    private fun param(label: String, min: Float, max: Float, step: Float, get: () -> Float, set: (Float) -> Unit): View {
        val value = text(format(get()), TEXT_BODY, R.color.windowTextSecondary)
        val seekBar = SeekBar(this).apply {
            this.max = ((max - min) / step).roundToInt()
            progress = ((get() - min) / step).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newValue = min + progress * step
                    set(newValue)
                    value.text = format(newValue)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        return row(label, value, seekBar)
    }

    private fun row(label: String, value: TextView, seekBar: SeekBar): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp(context), 0, 4.dp(context))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(label, TEXT_BODY, R.color.windowTextPrimary), linearParams(0, wrapContent, weight = 1f))
                addView(value, linearParams(wrapContent, wrapContent))
            }, linearParams(matchParent, wrapContent, marginLeft = margin, marginRight = margin))
            addView(seekBar, linearParams(matchParent, 40.dp(context)))
        }
    }

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun group(rows: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.layer1))
        val radius = 13f.dp(context)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
        rows.forEachIndexed { index, row ->
            if (index > 0) addView(View(context).apply { setBackgroundColor(getColor(R.color.windowDivider)) }, linearParams(matchParent, 1))
            addView(row, linearParams(matchParent, wrapContent))
        }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        typeface = font_regular
        includeFontPadding = false
        setTextColor(getColor(color))
    }
}
