package vadiole.unicode.ui.common

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import vadiole.unicode.R
import vadiole.unicode.ui.extension.onClick

class SearchBar(
    context: Context,
    private val delegate: Delegate,
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
    private val buttonWidth = buttonGap + fieldSize + edgePadding
    private val buttonHeight = fieldSize + touchExtension * 2

    private var isExpandedState = false

    private val surfaceDrawable = FloatingSurfaceDrawable(
        context = context,
        pathProvider = FloatingSurfaceDrawable.pill(),
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
        setPadding(iconInset + iconSize + 8.dp(context), touchExtension, edgePadding, touchExtension)
    }

    private val closeButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_close)!!,
        iconSize = iconSize,
        iconColor = context.getColor(R.color.windowTextPrimary),
    ).apply {
        setPadding(buttonGap, touchExtension, edgePadding, touchExtension)
        contentDescription = context.getString(R.string.search_close)
        onClick = { collapse() }
    }

    private val closeContainer = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(closeButton)
    }

    private val searchButton = IconButton(
        context = context,
        icon = context.getDrawable(R.drawable.ic_search)!!,
        iconSize = iconSize,
        iconColor = context.getColor(R.color.windowTextPrimary),
    ).apply {
        setPadding(buttonGap, touchExtension, edgePadding, touchExtension)
        contentDescription = context.getString(R.string.search_hint)
        onClick = { expand() }
    }

    private val searchContainer = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(searchButton)
    }

    private val progressHolder = FloatValueHolder(0f)
    private val progressSpring = SpringAnimation(progressHolder).apply {
        spring = SpringForce(0f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 500f
        }
        setMinimumVisibleChange(0.001f)
        addUpdateListener { _, value, _ -> applyProgress(value.coerceIn(0f, 1f)) }
    }
    private var isKeyboardPending = false

    init {
        clipChildren = false
        clipToPadding = false
        addView(surfaceView)
        addView(searchField)
        addView(closeContainer)
        addView(searchContainer)
        setSearchFieldEnabled(false)
        applyProgress(0f)
    }

    fun expand() {
        if (isExpandedState) return
        isExpandedState = true
        setSearchFieldEnabled(true)
        searchField.requestFocus()
        isKeyboardPending = true
        delegate.onExpanded()
        animateProgress(1f)
    }

    fun collapse() {
        if (!isExpandedState) return
        isExpandedState = false
        searchField.clearQuery()
        isKeyboardPending = false
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
        progressHolder.value = p
        applyProgress(p)
    }

    fun cancelScrub() = animateProgress(1f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fieldWidth = (width - edgePadding * 2 - fieldSize - buttonGap).coerceAtLeast(0)
        surfaceView.measure(exactly(width), exactly(height))
        searchField.measure(exactly(fieldWidth), exactly(buttonHeight))
        closeContainer.measure(exactly(buttonWidth), exactly(buttonHeight))
        searchContainer.measure(exactly(buttonWidth), exactly(buttonHeight))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val rowTop = fieldTop - touchExtension
        surfaceView.layout(0, 0, width, bottom - top)
        searchField.layout(edgePadding, rowTop, edgePadding + searchField.measuredWidth, rowTop + buttonHeight)
        val buttonLeft = width - buttonWidth
        closeContainer.layout(buttonLeft, rowTop, width, rowTop + buttonHeight)
        searchContainer.layout(buttonLeft, rowTop, width, rowTop + buttonHeight)
        closeContainer.pivotX = buttonGap + fieldSize / 2f
        closeContainer.pivotY = touchExtension + fieldSize / 2f
        searchContainer.pivotX = closeContainer.pivotX
        searchContainer.pivotY = closeContainer.pivotY
        val fieldRight = width - edgePadding - fieldSize - buttonGap
        surfaceDrawable.setBounds(edgePadding, fieldTop, fieldRight, fieldTop + fieldSize)
        val iconLeft = edgePadding + iconInset
        val iconTop = fieldTop + (fieldSize - iconSize) / 2
        fieldIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        surfaceView.pivotX = (edgePadding + fieldRight) / 2f
        surfaceView.pivotY = fieldTop + fieldSize / 2f
        searchField.pivotX = searchField.measuredWidth / 2f
        searchField.pivotY = touchExtension + fieldSize / 2f
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun animateProgress(target: Float) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progressSpring.cancel()
            progressHolder.value = target
            applyProgress(target)
            return
        }
        progressSpring.animateToFinalPosition(target)
    }

    private fun applyProgress(p: Float) {
        if (isKeyboardPending && p >= 0.7f) {
            isKeyboardPending = false
            showKeyboard()
        }
        val searchButtonProgress = ((p - 0.2f) / 0.75f).coerceIn(0f, 1f)
        val searchButtonScale = 1f - 0.2f * searchButtonProgress
        searchContainer.alpha = 1f - searchButtonProgress
        searchContainer.scaleX = searchButtonScale
        searchContainer.scaleY = searchButtonScale
        searchContainer.visibility = if (!isExpandedState || searchButtonProgress < 1f) VISIBLE else INVISIBLE
        setControlEnabled(searchButton, !isExpandedState)

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

        val closeProgress = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val closeScale = 0.9f + 0.1f * closeProgress
        closeContainer.alpha = closeProgress
        closeContainer.scaleX = closeScale
        closeContainer.scaleY = closeScale
        closeContainer.visibility = if (isExpandedState || closeProgress > 0f) VISIBLE else INVISIBLE
        setControlEnabled(closeButton, isExpandedState)

        delegate.onAnimationRunning(p)
    }

    private fun setControlEnabled(view: View, enabled: Boolean) {
        if (view.isEnabled == enabled) return
        view.isEnabled = enabled
        view.isClickable = enabled
        view.isFocusable = enabled
        view.importantForAccessibility = if (enabled) {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun setSearchFieldEnabled(enabled: Boolean) {
        if (enabled) searchField.visibility = VISIBLE
        searchField.isEnabled = enabled
        searchField.isFocusable = enabled
        searchField.isFocusableInTouchMode = enabled
        searchField.importantForAccessibility = if (enabled) {
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
        fun barHeight(context: Context): Int = 72.dp(context)
    }
}
