package vadiole.unicode.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import setCursorDrawable
import vadiole.unicode.R


import vadiole.unicode.utils.extension.dp
import vadiole.unicode.utils.extension.hideKeyboard

class SearchView(context: Context, private val delegate: Delegate) : EditText(context) {
    private val backgroundDrawable = SquircleDrawable(10.dp(context))
    private val cursorDrawable = context.getDrawable(R.drawable.cursor)!!
    private val magnifyingGlassDrawable = context.getDrawable(R.drawable.ic_magnifying_glass)!!
    private var lastInput = ""
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            val input = s.toString().trim()
            if (input != lastInput) {
                lastInput = input
                delegate.onTextChanged(input)
            }
        }
    }

    interface Delegate {
        fun onFocused(): Boolean
        fun onUnfocused(): Boolean
        fun onTextChanged(string: String)
    }

    init {
        applyTheme()
        setCursorDrawable(cursorDrawable)
        addTextChangedListener(textWatcher)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
        setPadding(8.dp(context), 0, 26.dp(context), 0)
        setCompoundDrawablesWithIntrinsicBounds(magnifyingGlassDrawable, null, null, null)
        isSingleLine = true
        typeface = roboto_regular
        includeFontPadding = false
        isLongClickable = false
        background = backgroundDrawable
        compoundDrawablePadding = 6.dp(context)
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_FORCE_ASCII
        inputType = InputType.TYPE_CLASS_TEXT
    }

    fun applyTheme() {
        backgroundDrawable.colors = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_pressed)
            ),
            intArrayOf(
                context.getColor(R.color.searchFieldSurface),
                context.getColor(R.color.searchFieldSurfacePressed)
            )
        )
        cursorDrawable.setTint(context.getColor(R.color.searchFieldCursor))
        magnifyingGlassDrawable.setTint(context.getColor(R.color.windowTextSecondary))
        setTextColor(context.getColor(R.color.windowTextPrimary))
        setHintTextColor(context.getColor(R.color.windowTextSecondary))
        highlightColor = context.getColor(R.color.windowTextSelection)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            delegate.onFocused()
        } else {
            delegate.onUnfocused()
            hideKeyboard()
            text.clear()
        }
    }
}