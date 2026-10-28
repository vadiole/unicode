package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import vadiole.unicode.R
import vadiole.unicode.ui.extension.onClick

class TopBar(
    context: Context,
    title: String,
    val centerTitle: Boolean = false,
    onTitleClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) : FrameLayout(context) {
    private val settingsMargin = 4.dp(context)
    private val settingsSize = 48.dp(context)
    private val sidePadding = if (centerTitle) 0 else 16.dp(context)
    private val titleEndPadding = if (centerTitle) 0 else 8.dp(context)
    private val reservedWidth = (settingsSize + settingsMargin) * if (centerTitle) 2 else 1
    private val chevron = context.getDrawable(R.drawable.ic_chevron_down)!!.mutate().apply {
        setTint(context.getColor(R.color.windowTextSecondary))
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }
    private val titleView = createTitle().apply {
        compoundDrawablePadding = 4.dp(context)
        ellipsize = TextUtils.TruncateAt.END
        text = title
        setCompoundDrawablesRelative(null, null, chevron, null)
    }
    private val searchTitle = createTitle().apply {
        text = context.getString(R.string.search_title)
        alpha = 0f
    }
    private val settingsButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_settings)!!,
        size = settingsSize,
        floating = false,
    ).apply {
        setPadding(0, 0, settingsMargin, 0)
        contentDescription = context.getString(R.string.settings_title)
        onClick = { onSettingsClick() }
    }

    private val titlePaint = Paint(titleView.paint)
    private var isSearchShown = false

    init {
        setBackgroundColor(this.context.getColor(R.color.layer1))
        onClick = { if (!isSearchShown) onTitleClick() }
        val titleGravity = (if (centerTitle) Gravity.CENTER_HORIZONTAL else Gravity.LEFT) or Gravity.CENTER_VERTICAL
        addView(titleView, frameParams(wrapContent, matchParent, gravity = titleGravity))
        addView(searchTitle, frameParams(wrapContent, matchParent, gravity = titleGravity))
        addView(settingsButton, frameParams(settingsSize + settingsMargin, matchParent, Gravity.RIGHT))
        addView(View(context).apply {
            setBackgroundColor(context.getColor(R.color.floatingBorder))
        }, frameParams(matchParent, borderWidth(context).toInt(), Gravity.BOTTOM))
    }

    private fun createTitle() = TextView(context).apply {
        setLineHeightX(22.dp(context))
        typeface = font_semibold
        includeFontPadding = false
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        setPadding(sidePadding, 0, titleEndPadding, 0)
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, MAX_TITLE_SP.toFloat())
        setTextColor(context.getColor(R.color.windowTextPrimary))
    }

    fun setTitle(text: String) {
        if (titleView.text.toString() == text) return
        titleView.text = text
        fitTitle()
    }

    fun setSearchShown(shown: Boolean) {
        if (isSearchShown == shown) return
        isSearchShown = shown
        settingsButton.isEnabled = !shown
        setContentAlpha(1f)
    }

    fun setSearchRecents(shown: Boolean) {
        searchTitle.setText(if (shown) R.string.search_recents else R.string.search_title)
    }

    fun setContentAlpha(alpha: Float) {
        val titleAlpha = if (isSearchShown) 0f else alpha
        titleView.alpha = titleAlpha
        settingsButton.alpha = titleAlpha
        searchTitle.alpha = if (isSearchShown) alpha else 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        titleView.maxWidth = w - reservedWidth
        fitTitle()
    }

    private fun fitTitle() {
        val available = width - reservedWidth - sidePadding - titleEndPadding -
            chevron.intrinsicWidth - titleView.compoundDrawablePadding
        if (available <= 0) return
        val text = titleView.text.toString()
        val metrics = resources.displayMetrics
        var fittingSp = MIN_TITLE_SP
        for (sizeSp in MAX_TITLE_SP downTo MIN_TITLE_SP) {
            titlePaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), metrics)
            if (titlePaint.measureText(text) <= available) {
                fittingSp = sizeSp
                break
            }
        }
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fittingSp.toFloat())
    }

    companion object {
        private const val MIN_TITLE_SP = 9
        private const val MAX_TITLE_SP = 17
    }
}
