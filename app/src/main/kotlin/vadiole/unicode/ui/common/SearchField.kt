package vadiole.unicode.ui.common

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import setCursorDrawable
import vadiole.unicode.R

class SearchField(context: Context, private val onQueryChanged: (String) -> Unit) : EditText(context) {
    private var lastInput = ""
    private var isReportScheduled = false
    private val choreographer = Choreographer.getInstance()
    private val reportQuery = Choreographer.FrameCallback {
        isReportScheduled = false
        val input = text.toString()
        if (input != lastInput) {
            lastInput = input
            onQueryChanged(input)
        }
    }
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            if (isReportScheduled) return
            isReportScheduled = true
            choreographer.postFrameCallback(reportQuery)
        }
    }

    init {
        val cursorDrawable = context.getDrawable(R.drawable.cursor)!!
        cursorDrawable.setTint(this.context.getColor(R.color.searchFieldCursor))
        setCursorDrawable(cursorDrawable)
        setTextColor(this.context.getColor(R.color.windowTextPrimary))
        setHintTextColor(this.context.getColor(R.color.windowTextSecondary))
        highlightColor = this.context.getColor(R.color.windowTextSelection)
        background = null
        addTextChangedListener(textWatcher)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BODY)
        isSingleLine = true
        typeface = font_regular
        includeFontPadding = false
        isLongClickable = true
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        choreographer.removeFrameCallback(reportQuery)
        isReportScheduled = false
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        background?.state = if (isEnabled && isPressed) PRESSED_STATE else EMPTY_STATE
    }

    fun clearQuery() {
        lastInput = ""
        setText("")
    }

    private companion object {
        val PRESSED_STATE = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
        val EMPTY_STATE = intArrayOf()
    }
}
