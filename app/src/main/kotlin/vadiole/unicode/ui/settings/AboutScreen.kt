package vadiole.unicode.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import vadiole.unicode.BuildConfig
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.ui.common.ActionCell
import vadiole.unicode.ui.common.EmojiFireworksView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SettingsGroup
import vadiole.unicode.ui.common.SettingsList
import vadiole.unicode.ui.common.SettingsTopBar
import vadiole.unicode.ui.common.TickHaptic
import vadiole.unicode.ui.common.TouchEffect
import vadiole.unicode.ui.common.addSquircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.common.onClick
import vadiole.unicode.ui.common.onLongClick

class AboutScreen(context: Context, onBack: () -> Unit) : Screen(context) {
    private val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val appInfo = listOf(
        "App: $appVersion",
        "Android: ${Build.VERSION.RELEASE}",
        "Config: ${ConfigCode.encode(userConfig)}",
    ).joinToString("\n")

    private val fireworks = EmojiFireworksView(context, FIREWORK_EMOJIS)

    private val list = SettingsList(context).apply {
        add(AppIcon(context, fireworks), marginTop = 32.dp(context), gravity = Gravity.CENTER_HORIZONTAL, width = 112.dp(context), height = 112.dp(context))
        add(SettingsGroup(context, listOf(
            ActionCell(context, context.getString(R.string.about_app_version)).apply {
                value = appVersion
                onLongClick = {
                    context.toClipboard(context.getString(R.string.app_name), appInfo)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.about_info_copied, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            ActionCell(context, context.getString(R.string.about_unicode_version)).apply {
                value = BuildConfig.UNICODE_VERSION
                setChevron()
                onClick = { openLink("https://www.unicode.org/versions/Unicode${BuildConfig.UNICODE_VERSION}/") }
            },
        )), marginTop = 32.dp(context))
        add(SettingsGroup(context, listOf(
            ActionCell(context, context.getString(R.string.about_rate)).apply {
                setChevron()
                onClick = { openLink("https://play.google.com/store/apps/details?id=vadiole.unicode") }
            },
            ActionCell(context, context.getString(R.string.about_feedback)).apply {
                setChevron()
                onClick = { sendFeedback() }
            },
        )))
        addFootnote(context.getString(R.string.about_feedback_note))
        add(SettingsGroup(context, listOf(
            ActionCell(context, context.getString(R.string.about_author)).apply {
                setChevron()
                onClick = { openLink("https://vadiole.me") }
            },
        )), marginTop = 20.dp(context))
    }

    init {
        setBackgroundColor(context.getColor(R.color.layer0))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(SettingsTopBar(context, context.getString(R.string.about_title), onBack).apply {
                addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ -> fireworks.topInset = bottom }
            }, linearParams(matchParent, wrapContent))
            addView(list, linearParams(matchParent, 0, weight = 1f))
        }, frameParams(matchParent, matchParent))
        addView(fireworks, frameParams(matchParent, matchParent))
    }

    private fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFeedback() {
        val subject = Uri.encode(context.getString(R.string.about_feedback_subject))
        val body = Uri.encode("$DIVIDER\n$appInfo\n$DIVIDER\n\n")
        val uri = Uri.parse("mailto:unicode@vadiole.me?subject=$subject&body=$body")
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.crash_no_email_clients, Toast.LENGTH_SHORT).show()
        }
    }

    private class AppIcon(context: Context, private val fireworks: EmojiFireworksView) : View(context) {
        private val path = Path()
        private val backgroundIcon = context.getDrawable(R.drawable.ic_launcher_background)!!
        private val foregroundIcon = context.getDrawable(R.drawable.ic_launcher_foreground)!!
        private val tickHaptic = TickHaptic(this, 0.8f)
        private var holding = false
        private val tick = object : Runnable {
            override fun run() {
                if (!holding) return
                tickHaptic.perform()
                postDelayed(this, TICK_INTERVAL)
            }
        }
        private val complete = Runnable {
            if (holding && fireworks.isReady) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                fireworks.start()
            }
            stopHold()
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener(TouchEffect(maxDragPx = 16f.dp(context), pressedScale = 1.08f))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    stopHold()
                    if (fireworks.isReady) {
                        holding = true
                        postDelayed(tick, TICK_INTERVAL)
                        postDelayed(complete, HOLD_DURATION)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopHold()
            }
            return true
        }

        private fun stopHold() {
            holding = false
            removeCallbacks(tick)
            removeCallbacks(complete)
        }

        override fun onDetachedFromWindow() {
            stopHold()
            super.onDetachedFromWindow()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            path.rewind()
            path.addSquircle(RectF(0f, 0f, w.toFloat(), h.toFloat()), 28.dp(context).toFloat())
            backgroundIcon.setBounds(-w / 4, -h / 4, w * 5 / 4, h * 5 / 4)
            foregroundIcon.bounds = backgroundIcon.bounds
        }

        override fun onDraw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipPath(path)
            backgroundIcon.draw(canvas)
            foregroundIcon.draw(canvas)
            canvas.restoreToCount(count)
        }

        private companion object {
            const val HOLD_DURATION = 600L
            const val TICK_INTERVAL = 100L
        }
    }

    private companion object {
        val FIREWORK_EMOJIS = listOf(
            "🧉", "🪎", "🔩", "⚗️", "🪠", "🐦‍🔥", "🫯", "🥻", "🐞", "🌈", "❤️", "🤍", "💛", "💙", "🦋", "🩷", "💜", "🩵",
            "🍓", "🍒", "🥐", "🍁", "🛰️", "🪐", "🧬", "🦾", "🪵", "🍄", "⚖️", "☄️", "🔬", "🌊", "⚡", "🎞️", "📸", "🌅",
            "🪁", "🚁", "🎹", "🧭", "⛵", "🏕️", "🪢", "🪉", "🪼", "🪸", "🍋", "🕯️", "🪴", "🌱", "🌵", "🏔️", "🐬",
        )
        const val DIVIDER = "— — — — — — — —"
    }
}
