package vadiole.unicode.ui.common

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import vadiole.unicode.R
import vadiole.unicode.ui.extension.onClick
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class SearchBar(
    context: Context,
    private val delegate: Delegate,
    private val atTop: Boolean = false,
) : FrameLayout(context) {

    interface Delegate {
        fun onExpanded()
        fun onCollapsed()
        fun onTextChanged(query: String)
        fun onAnimationRunning(progress: Float)
    }

    private val edgePadding = 16.dp(context)
    private val fieldSize = 48.dp(context)
    private val fieldTop = 8.dp(context)
    private val buttonGap = 8.dp(context)
    private val touchExtension = 8.dp(context)
    private val iconSize = 24.dp(context)
    private val iconInset = 12.dp(context)
    private val buttonSize = 52.dp(context)
    private val buttonInset = touchExtension + (fieldSize - buttonSize) / 2
    private val buttonWidth = if (atTop) buttonSize else buttonGap + buttonSize + edgePadding
    private val buttonHeight = fieldSize + touchExtension * 2

    private var isExpandedState = false

    private val surfaceDrawable = FloatingSurfaceDrawable(
        context = context,
        pathProvider = FloatingSurfaceDrawable.pill(),
        shadow = SearchShadowDrawable(context),
    )
    private val fieldIcon = context.getDrawable(R.drawable.ic_search)!!.mutate().apply {
        setTint(context.getColor(R.color.windowTextSecondary))
    }

    private val surfaceView = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            surfaceDrawable.draw(canvas)
            fieldIcon.draw(canvas)
        }
    }

    private val searchField = SearchField(context) { query -> delegate.onTextChanged(query) }.apply {
        hint = context.getString(R.string.search_hint)
        val radius = fieldSize / 2f
        val mask = ShapeDrawable(RoundRectShape(FloatArray(8) { radius }, null, null))
        background = InsetDrawable(context.rippleDrawable(mask = mask), 0, touchExtension, 0, touchExtension)
        setPadding(iconInset + iconSize + 8.dp(context), touchExtension, edgePadding, touchExtension)
        if (atTop) {
            onClick = { expand() }
            setOnFocusChangeListener { _, focused -> if (focused) expand() }
        }
    }

    private val actionButton = IconButton(
        context = context,
        icon = context.getDrawable(if (atTop) R.drawable.ic_close else R.drawable.ic_search)!!,
        altIcon = if (atTop) null else context.getDrawable(R.drawable.ic_close)!!,
        iconSize = iconSize,
        size = buttonSize,
        surfaceShadow = SearchShadowDrawable(context),
    ).apply {
        setPadding(if (atTop) 0 else buttonGap, buttonInset, if (atTop) 0 else edgePadding, buttonInset)
        contentDescription = context.getString(R.string.search_hint)
        onClick = { if (isExpandedState) collapse() else expand() }
    }

    private val actionContainer = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(actionButton)
    }

    private val progressSpring = SpringAnimator(
        stiffness = 500f,
        minVisibleChange = 0.001f,
        onUpdate = { applyProgress(it.coerceIn(0f, 1f)) },
    )

    init {
        clipChildren = false
        clipToPadding = false
        addView(surfaceView)
        addView(searchField)
        addView(actionContainer)
        setSearchFieldEnabled(false)
        applyProgress(0f, notify = false)
    }

    fun expand() {
        if (isExpandedState) return
        isExpandedState = true
        actionButton.contentDescription = context.getString(R.string.search_close)
        setSearchFieldEnabled(true)
        searchField.requestFocus()
        showKeyboard()
        delegate.onExpanded()
        animateProgress(1f)
    }

    fun collapse() {
        if (!isExpandedState) return
        isExpandedState = false
        actionButton.contentDescription = context.getString(if (atTop) R.string.search_close else R.string.search_hint)
        searchField.clearQuery()
        hideKeyboard()
        searchField.clearFocus()
        setSearchFieldEnabled(false)
        delegate.onCollapsed()
        animateProgress(0f)
    }

    fun isExpanded(): Boolean = isExpandedState

    fun startScrub() {
        progressSpring.cancel()
    }

    fun scrub(p: Float) {
        progressSpring.value = p
        applyProgress(p)
    }

    fun cancelScrub() = animateProgress(1f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fieldWidth = (fieldRight(width, progressSpring.value.coerceIn(0f, 1f)) - edgePadding).coerceAtLeast(0)
        surfaceView.measure(exactly(width), exactly(height))
        searchField.measure(exactly(fieldWidth), exactly(buttonHeight))
        actionContainer.measure(exactly(buttonWidth), exactly(buttonHeight))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val rowTop = fieldTop - touchExtension
        surfaceView.layout(0, 0, width, bottom - top)
        layoutField(progressSpring.value.coerceIn(0f, 1f))
        val buttonRight = if (atTop) width - edgePadding else width
        val buttonLeft = buttonRight - buttonWidth
        actionContainer.layout(buttonLeft, rowTop, buttonRight, rowTop + buttonHeight)
        actionContainer.pivotX = (if (atTop) 0 else buttonGap) + buttonSize / 2f
        actionContainer.pivotY = buttonHeight / 2f
        val fieldRight = fieldRight(width, 0f)
        val iconLeft = edgePadding + iconInset
        val iconTop = fieldTop + (fieldSize - iconSize) / 2
        fieldIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        surfaceView.pivotX = (edgePadding + fieldRight) / 2f
        surfaceView.pivotY = fieldTop + fieldSize / 2f
        searchField.pivotX = searchField.measuredWidth / 2f
        searchField.pivotY = touchExtension + fieldSize / 2f
    }

    private fun fieldRight(width: Int, p: Float): Int {
        val actionSpace = if (atTop) ((buttonSize + buttonGap) * p).roundToInt() else buttonSize + buttonGap
        return width - edgePadding - actionSpace
    }

    private fun layoutField(p: Float) {
        if (width == 0) return
        val fieldRight = fieldRight(width, p)
        val fieldWidth = (fieldRight - edgePadding).coerceAtLeast(0)
        if (searchField.measuredWidth != fieldWidth) {
            searchField.measure(exactly(fieldWidth), exactly(buttonHeight))
        }
        val rowTop = fieldTop - touchExtension
        val layoutRight = if (atTop) fieldRight else edgePadding + fieldWidth
        searchField.layout(edgePadding, rowTop, layoutRight, rowTop + buttonHeight)
        surfaceDrawable.setBounds(edgePadding, fieldTop, fieldRight, fieldTop + fieldSize)
        surfaceView.invalidate()
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun animateProgress(target: Float) {
        if (!animatorsEnabled) {
            progressSpring.cancel()
            progressSpring.value = target
            applyProgress(target)
            return
        }
        progressSpring.animateTo(target)
    }

    private fun applyProgress(p: Float, notify: Boolean = true) {
        if (atTop) {
            applyTopProgress(p)
        } else {
            applyBottomProgress(p)
        }
        if (notify) delegate.onAnimationRunning(p)
    }

    private fun applyTopProgress(p: Float) {
        layoutField(p)
        actionContainer.translationX = (buttonSize + edgePadding) * (1f - p)
        actionContainer.visibility = if (p > 0f) VISIBLE else INVISIBLE
    }

    private fun applyBottomProgress(p: Float) {
        val actionScale = 1f - ACTION_PULSE * sin(PI.toFloat() * p)
        actionContainer.scaleX = actionScale
        actionContainer.scaleY = actionScale
        actionButton.iconProgress = p

        val fieldProgress = (p / 0.6f).coerceIn(0f, 1f)
        val fieldScale = 1.08f - 0.08f * p
        surfaceView.alpha = fieldProgress
        surfaceView.scaleX = fieldScale
        surfaceView.scaleY = fieldScale
        surfaceView.visibility = if (p > 0f) VISIBLE else INVISIBLE
        searchField.alpha = fieldProgress
        searchField.scaleX = fieldScale
        searchField.scaleY = fieldScale
        if (!isExpandedState && p <= 0f) searchField.visibility = INVISIBLE
    }

    private fun setSearchFieldEnabled(enabled: Boolean) {
        if (atTop) {
            actionButton.isEnabled = enabled
            actionButton.importantForAccessibility = if (enabled) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (enabled || atTop) searchField.visibility = VISIBLE
        searchField.isEnabled = enabled || atTop
        searchField.isFocusable = enabled || atTop
        searchField.isFocusableInTouchMode = enabled
        searchField.isCursorVisible = enabled
        searchField.importantForAccessibility = if (enabled || atTop) {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun showKeyboard() {
        val activity = context as? Activity
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, searchField).show(WindowInsetsCompat.Type.ime())
        } else {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchField, 0)
        }
    }

    private fun hideKeyboard() {
        val activity = context as? Activity
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, searchField).hide(WindowInsetsCompat.Type.ime())
        } else {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressSpring.cancel()
    }

    companion object {
        private const val ACTION_PULSE = 0.12f

        fun barHeight(context: Context): Int = 72.dp(context)
    }
}
