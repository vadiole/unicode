package vadiole.unicode.ui.details

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.view.ViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vadiole.unicode.R
import vadiole.unicode.data.CharObj
import vadiole.unicode.data.CharStorage
import vadiole.unicode.ui.common.*
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.extension.*

class DetailsSheet(
    context: Context,
    theme: AppTheme,
    private val charStorage: CharStorage,
) : Screen(context), ThemeDelegate {
    private var charObj: CharObj? = null
    private val screenPadding = 20.dp(context)
    private val verticalPadding = 10.dp(context)
    private val backgroundDrawable = SquircleDrawable(20.dp(context)).apply {
        skipBottomRight = true
        skipBottomLeft = true
    }
    private val backgroundPaint = Paint()
    private var vertical = 0
    private val titleHeight = 21.dp(context)
    private val title = TextView(context).apply(fun TextView.() {
        layoutParams = frameParams(matchParent, titleHeight, gravity = Gravity.TOP)
        vertical += titleHeight
        ellipsize = TextUtils.TruncateAt.END
        typeface = roboto_semibold
        gravity = Gravity.LEFT
        letterSpacing = 0.03f
        isSingleLine = true
        textSize = 16f
    })
    private val subtitleHeight = 18.dp(context)
    private val subtitle = TextView(context).apply(fun TextView.() {
        layoutParams = frameParams(matchParent, subtitleHeight, gravity = Gravity.TOP, marginTop = vertical)
        vertical += subtitleHeight + verticalPadding
        ellipsize = TextUtils.TruncateAt.END
        typeface = roboto_regular
        letterSpacing = 0.02f
        gravity = Gravity.LEFT
        isSingleLine = true
        textSize = 13f
    })
    private var divider1PositionY = 2f * screenPadding + titleHeight + subtitleHeight
    private val charViewHeight = 200.dp(context)
    private val charView = SimpleTextView(context).apply {
        vertical += verticalPadding
        layoutParams = frameParams(matchParent, charViewHeight, gravity = Gravity.TOP, marginTop = vertical)
        vertical += charViewHeight
        textSize = 100f.dp(context)
    }
    private val infoViewHeight = 56.dp(context)
    private val infoViews = List(4) {
        InfoView(context, theme).apply {
            layoutParams = linearParams(matchParent, infoViewHeight, weight = 1f)
            onLongClick = {
                charObj?.let { value ->
                    val info = value.infoValues[it]
                    context.toClipboard("Unicode", info)
                    Toast.makeText(context, "$info copied to clipboard", LENGTH_SHORT).show()
                }
            }
        }
    }
    private val infoViewsContainer = LinearLayout(context).apply {
        layoutParams = frameParams(matchParent, infoViewHeight, Gravity.TOP, marginTop = vertical)
        vertical += infoViewHeight + verticalPadding * 3
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = SpacerDrawable(width = 8.dp(context))
        infoViews.forEach { infoView ->
            addView(infoView)
        }
    }
    private val actionCellHeight = 48.dp(context)
    private val actionCopy = ActionCell(context, theme, "Copy to Clipboard", topItem = true).apply {
        layoutParams = frameParams(matchParent, actionCellHeight, marginTop = vertical)
        setIcon(R.drawable.ic_copy)
        vertical += actionCellHeight
        onClick = {
            charObj?.let { value ->
                val char = value.char
                context.toClipboard("Unicode", char)
                Toast.makeText(context, "$char copied to clipboard", LENGTH_SHORT).show()
            }
        }
    }
    private var divider2PositionY = vertical.toFloat() + screenPadding

    private val actionShare = ActionCell(context, theme, "Share Link", bottomItem = true).apply {
        layoutParams = frameParams(matchParent, actionCellHeight, marginTop = vertical)
        setIcon(R.drawable.ic_link)
        vertical += actionCellHeight

        var canClick = true
        onClick = {
            if (canClick) {
                launch {
                    canClick = false
                    charObj?.let { value ->
                        val charId = value.id
                        val link = "vadiole.github.io/unicode?c=$charId"
                        context.share(link)
                    }
                    delay(500)
                    canClick = true
                }
            }
        }
        onLongClick = {
            charObj?.let { value ->
                val charId = value.id
                val link = "vadiole.github.io/unicode?c=$charId"
                context.toClipboard("Unicode", link)
                Toast.makeText(context, "Link copied to clipboard", LENGTH_SHORT).show()
            }
        }
    }

    init {
        theme.observe(this)
        background = backgroundDrawable
        val height = vertical + screenPadding * 2 + 40.dp(context)
        layoutParams = frameParams(matchParent, height, gravity = Gravity.BOTTOM)
        clipChildren = false
        setPadding(screenPadding)
        setWillNotDraw(false)
        addView(title)
        addView(subtitle)
        addView(charView)
        addView(infoViewsContainer)
        addView(actionCopy)
        addView(actionShare)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bottomInset = insets.navigationBars.bottom
            layoutParams = frameParams(matchParent, bottomInset + height, gravity = Gravity.BOTTOM)
            insets
        }
    }

    // TODO: add strings to localeManager
    private val infoNames: Array<String> = arrayOf("Code", "HTML", "CSS", "Version")
    fun bind(codePoint: Int) = launch {
        val obj: CharObj = charStorage.getCharObj(codePoint) //fix clipping char id=21687
        title.text = obj.description
        subtitle.text = "Here will be placed a description of the block"
        charView.text = obj.char
        infoViews.forEachIndexed { index, infoView ->
            val name = infoNames[index]
            val value = obj.infoValues[index]
            infoView.bind(name, value)
        }
        charObj = obj
    }

    override fun applyTheme(theme: Theme) {
        backgroundDrawable.colors = theme.getColors(key_dialogBackground)
        backgroundPaint.color = theme.getColor(key_dialogBackground)
        charView.textColor = theme.getColor(key_windowTextPrimary)
        title.setTextColor(theme.getColor(key_windowTextPrimary))
        subtitle.setTextColor(theme.getColor(key_windowTextSecondary))
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(
            0f, measuredHeight - 20f.dp(context), measuredWidth.toFloat(), measuredHeight * 200f,
            backgroundPaint
        )
        super.draw(canvas)
        canvas.drawLine(0f, divider1PositionY, measuredWidth.toFloat(), divider1PositionY, sharedDividerPaint)
        canvas.drawLine(
            screenPadding.toFloat(),
            divider2PositionY,
            measuredWidth.toFloat() - screenPadding, divider2PositionY,
            sharedDividerPaint
        )
    }
}