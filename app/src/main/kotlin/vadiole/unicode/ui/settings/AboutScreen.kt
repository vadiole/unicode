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
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import vadiole.unicode.BuildConfig
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.ui.common.ActionCell
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SettingsTopBar
import vadiole.unicode.ui.common.SettingsGroup
import vadiole.unicode.ui.common.SettingsList
import vadiole.unicode.ui.common.addSquircle
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick
import vadiole.unicode.ui.extension.onLongClick

class AboutScreen(context: Context, onBack: () -> Unit) : Screen(context) {
    private val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val appInfo = listOf(
        "App: $appVersion",
        "Android: ${Build.VERSION.RELEASE}",
        "Config: ${ConfigCode.encode(userConfig)}",
    ).joinToString("\n")

    private val list = SettingsList(context).apply {
        add(AppIcon(context), marginTop = 32.dp(context), gravity = Gravity.CENTER_HORIZONTAL, width = 112.dp(context), height = 112.dp(context))
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
            addView(SettingsTopBar(context, context.getString(R.string.about_title), onBack), linearParams(matchParent, wrapContent))
            addView(list, linearParams(matchParent, 0, weight = 1f))
        }, frameParams(matchParent, matchParent))
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

    private class AppIcon(context: Context) : View(context) {
        private val path = Path()
        private val backgroundIcon = context.getDrawable(R.drawable.ic_launcher_background)!!
        private val foregroundIcon = context.getDrawable(R.drawable.ic_launcher_foreground)!!

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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
    }

    private companion object {
        const val DIVIDER = "- - - - - - - - - -"
    }
}
