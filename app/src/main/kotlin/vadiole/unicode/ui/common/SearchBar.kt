package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import vadiole.unicode.R


import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.frameParams
import vadiole.unicode.utils.extension.matchParent
import vadiole.unicode.utils.extension.onClick
import vadiole.unicode.utils.extension.wrapContent

class SearchBar(context: Context, delegate: Delegate) : FrameLayout(context) {

    interface Delegate {
        fun onFocused(): Boolean
        fun onUnfocused(): Boolean
        fun onTextChanged(string: String)
        fun onAnimationRunning(progress: Float)
    }

    private val searchViewDelegate = object : SearchView.Delegate {
        override fun onFocused(): Boolean {
            toggleCancelButton(true)
            return delegate.onFocused()
        }

        override fun onUnfocused(): Boolean {
            toggleCancelButton(false)
            return delegate.onUnfocused()
        }

        override fun onTextChanged(string: String) {
            delegate.onTextChanged(string)
        }
    }
    val searchView = SearchView(context, searchViewDelegate).apply {
        hint = "Search"
    }
    private val cancelMaxTranslation: Float
        get() = cancelButton.measuredWidth - 14f.dp(context)
    private val cancelButton = TextButton(context).apply {
        text = "Cancel"
        setPadding(14.dp(context), 0, 16.dp(context), 0)
        onClick = {
            searchView.clearFocus()
        }
        doOnLayout {
            translationX = cancelMaxTranslation
        }
    }
    private val animator = SpringAnimation(cancelButton, DynamicAnimation.TRANSLATION_X).apply {
        addUpdateListener { _, translationX, _ ->
            searchView.updateLayoutParams<MarginLayoutParams> {
                val translation = cancelButton.measuredWidth - translationX
                setMargins(0, 0, translation.toInt(), 0)
                val progress = 1 - translationX / cancelMaxTranslation
                Log.v("SPRING", "progress $progress")
                cancelButton.alpha = progress
                delegate.onAnimationRunning(progress)
            }
        }
    }

    init {
        addView(searchView, frameParams(matchParent, 36.dp(context), gravity = Gravity.CENTER_VERTICAL, marginRight = 16.dp(context)))
        addView(cancelButton, frameParams(wrapContent, matchParent, gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT))
        setPadding(16.dp(context), 0, 0, 0)
        clipToPadding = false
        applyTheme()
    }

    private fun toggleCancelButton(show: Boolean) {
        val finalPosition = if (show) 0f else cancelMaxTranslation
        animator.cancel()
        animator.spring = SpringForce(finalPosition).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 600f
        }
        animator.start()
    }

    fun applyTheme() {
        cancelButton.colors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_pressed),
            ),
            intArrayOf(
                context.getColor(R.color.windowTextAccentPressed),
                context.getColor(R.color.windowTextAccent),
            )
        )
        background = ColorDrawable(context.getColor(R.color.topBarBackground))
    }
}