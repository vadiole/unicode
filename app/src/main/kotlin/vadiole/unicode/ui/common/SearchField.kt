package vadiole.unicode.ui.common

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import setCursorDrawable
import vadiole.unicode.R

class SearchField(context: Context, private val onQueryChanged: (String) -> Unit) : EditText(context) {
    private var lastInput = ""
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            val input = s.toString()
            if (input != lastInput) {
                lastInput = input
                onQueryChanged(input)
            }
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
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        isSingleLine = true
        typeface = mono_regular
        includeFontPadding = false
        isLongClickable = true
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_FORCE_ASCII
        inputType = InputType.TYPE_CLASS_TEXT
    }

    fun clearQuery() {
        lastInput = ""
        setText("")
    }
}
