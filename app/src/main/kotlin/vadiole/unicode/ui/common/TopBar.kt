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
    onTitleClick: TextView.() -> Unit = {},
    onSettingsClick: () -> Unit = {},
) : FrameLayout(context) {
    private val sidePadding = 16.dp(context)
    private val titleEndPadding = 8.dp(context)
    private val settingsMargin = 4.dp(context)
    private val settingsSize = 48.dp(context)
    private val chevron = context.getDrawable(R.drawable.ic_chevron_down)!!.mutate().apply {
        setTint(context.getColor(R.color.windowTextSecondary))
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }
    val titleView = createTitle().apply {
        onClick = onTitleClick
        compoundDrawablePadding = 4.dp(context)
        ellipsize = TextUtils.TruncateAt.END
        text = title
        setCompoundDrawablesRelative(null, null, chevron, null)
    }
    private val recentsTitle = createTitle().apply {
        text = context.getString(R.string.search_recents)
        alpha = 0f
    }
    private val searchTitle = createTitle().apply {
        text = context.getString(R.string.search_title)
        alpha = 0f
    }
    private val settingsButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_settings)!!,
        size = settingsSize,
        iconColor = context.getColor(R.color.windowTextSecondary),
        floating = false,
    ).apply {
        contentDescription = context.getString(R.string.settings_title)
        onClick = { onSettingsClick() }
    }

    private val titlePaint = Paint(titleView.paint)
    private var isSearchShown = false
    private var hasQuery = false

    init {
        setBackgroundColor(this.context.getColor(R.color.layer1))
        val titleGravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        addView(titleView, frameParams(wrapContent, matchParent, gravity = titleGravity))
        addView(recentsTitle, frameParams(wrapContent, matchParent, gravity = titleGravity))
        addView(searchTitle, frameParams(wrapContent, matchParent, gravity = titleGravity))
        addView(settingsButton, frameParams(settingsSize, settingsSize, Gravity.RIGHT or Gravity.CENTER_VERTICAL, marginRight = settingsMargin))
        addView(View(context).apply {
            setBackgroundColor(context.getColor(R.color.floatingBorder))
        }, frameParams(matchParent, borderWidth(context).toInt(), Gravity.BOTTOM))
    }

    private fun createTitle() = TextView(context).apply {
        setLineHeightX(22.dp(context))
        typeface = mono_semibold
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
        val alpha = if (shown) 0f else 1f
        titleView.alpha = alpha
        settingsButton.alpha = alpha
        titleView.isEnabled = !shown
        settingsButton.isEnabled = !shown
        applySearchTitles()
    }

    fun setSearchTitle(hasQuery: Boolean) {
        this.hasQuery = hasQuery
        applySearchTitles()
    }

    private fun applySearchTitles() {
        recentsTitle.alpha = if (isSearchShown && !hasQuery) 1f else 0f
        searchTitle.alpha = if (isSearchShown && hasQuery) 1f else 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        titleView.maxWidth = w - settingsSize - settingsMargin
        fitTitle()
    }

    private fun fitTitle() {
        val available = width - settingsSize - settingsMargin - sidePadding - titleEndPadding -
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
