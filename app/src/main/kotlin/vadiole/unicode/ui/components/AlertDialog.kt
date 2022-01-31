package vadiole.unicode.ui.components

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.StateSet
import android.util.TypedValue
import android.view.*
import android.view.Gravity.*
import android.view.View
import android.widget.*
import vadiole.unicode.R
import vadiole.unicode.ui.theme.*
import vadiole.unicode.utils.*

class AlertDialog constructor(
    context: Context,
    private val appTheme: Theme,
    private val title: CharSequence? = null,
    private val message: CharSequence? = null,
    private val items: Array<CharSequence>? = null,
    private val itemIcons: IntArray? = null,
    private val buttonPositiveText: CharSequence? = null,
    private val buttonNegativeText: CharSequence? = null,
    private val buttonNeutralText: CharSequence? = null,
    private val topView: View? = null,
    private val customView: View? = null,
    private val isDangerDialog: Boolean = false,
    private val dismissDialogByButtons: Boolean = true,
    private var verticalButtons: Boolean = false,
    private var isFullscreen: Boolean = false,
) : Dialog(context, R.style.Theme_Dialog_Transparent), Drawable.Callback {

    private var contentScroll: ScrollView? = null
    private var contentLayout: LinearLayout? = null
    private var titleLayout: FrameLayout? = null
    private var titleView: TextView? = null
    private var messageView: TextView? = null
    private val itemViews = mutableListOf<AlertDialogCell>()
    private var buttonsLayout: ViewGroup? = null

    private val backgroundPaddings = Rect()
    private var shadowDrawable = context.getDrawable(R.drawable.background_alert_dialog)!!.mutate().apply {
        colorFilter = appTheme.getTint(key_dialogBackground)
        getPadding(backgroundPaddings)
    }

    private val customViewOffset = 20.dp(context)
    private var aspectRatio: Float = 0f

    private var onClickListener: DialogInterface.OnClickListener? = null
    private var onDismissListener2: DialogInterface.OnDismissListener? = null
    private var onPositiveButtonListener: DialogInterface.OnClickListener? = null
    private var onNegativeButtonListener: DialogInterface.OnClickListener? = null
    private var onNeutralButtonListener: DialogInterface.OnClickListener? = null
    private var buttonBackListener: DialogInterface.OnClickListener? = null


    override fun onCreate(savedInstanceState: Bundle?) = with(context) {
        super.onCreate(savedInstanceState)

        val root = object : LinearLayout(context) {

            init {
                background = shadowDrawable
                orientation = VERTICAL
            }

            private var inLayout = false

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                inLayout = true
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = MeasureSpec.getSize(heightMeasureSpec)
                val maxContentHeight = height - paddingTop - paddingBottom
                var availableHeight = maxContentHeight
                val availableWidth = width - paddingLeft - paddingRight
                val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    availableWidth - 48.dp(context), MeasureSpec.EXACTLY
                )
                val childFullWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    availableWidth, MeasureSpec.EXACTLY
                )
                var layoutParams: LayoutParams

                buttonsLayout?.apply {
                    for (a in 0 until childCount) {
                        val child = getChildAt(a)
                        if (child is TextView) {
                            child.maxWidth = ((availableWidth - 24.dp(context)) / 2).dp(context)
                        }
                    }

                    measure(childFullWidthMeasureSpec, heightMeasureSpec)
                    layoutParams = this.layoutParams as LayoutParams
                    availableHeight -= measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
                }

                titleView?.measure(childWidthMeasureSpec, heightMeasureSpec)

                titleLayout?.apply {
                    measure(childWidthMeasureSpec, heightMeasureSpec)
                    layoutParams = getLayoutParams() as LayoutParams
                    availableHeight -= measuredHeight + layoutParams.bottomMargin + layoutParams.topMargin
                }

                topView?.apply {
                    val w = width - 16.dp(context)
                    val h: Int = if (aspectRatio == 0f) {
                        val scale = w / 936.0f
                        (354f * scale).toInt()
                    } else {
                        (w * aspectRatio).toInt()
                    }
                    measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                    )
                    getLayoutParams().height = h
                    availableHeight -= measuredHeight
                }

                layoutParams = contentScroll!!.layoutParams as LayoutParams

                when {
                    customView != null -> {
                        layoutParams.topMargin =
                            if (titleView == null && messageView == null && items == null) 16.dp(context) else 0
                        layoutParams.bottomMargin = if (buttonsLayout == null) 8.dp(context) else 0
                    }
                    items != null -> {
                        layoutParams.topMargin =
                            if (titleView == null && messageView == null) 8.dp(context) else 0
                        layoutParams.bottomMargin = if (buttonsLayout == null) 8.dp(context) else 0
                    }
                    messageView != null -> {
                        layoutParams.topMargin = if (titleView == null) 19.dp(context) else 0
                        layoutParams.bottomMargin = 20.dp(context)
                    }
                }

                availableHeight -= layoutParams.bottomMargin + layoutParams.topMargin
                contentScroll!!.measure(
                    childFullWidthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST)
                )
                availableHeight -= contentScroll!!.measuredHeight

                setMeasuredDimension(width, maxContentHeight - availableHeight + paddingTop + paddingBottom)
                inLayout = false
            }


            override fun requestLayout() {
                if (inLayout) return
                super.requestLayout()
            }

            override fun hasOverlappingRendering(): Boolean {
                return false
            }
        }

        setContentView(root)

        if (title != null) {
            titleLayout = FrameLayout(context).apply {
                titleView = TextView(context).apply {
                    setTextColor(appTheme.getColor(key_dialogTextPrimary))
                    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
                    gravity = LEFT or TOP
                    text = title
                }

                val marginTop = 19.dp(context)
                val marginBottom = if (items != null) 14.dp(context) else 10.dp(context)
                val margins = Rect(0, marginTop, 0, marginBottom)
                addView(titleView, frameParams(matchParent, wrapContent, LEFT, margins))
            }

            val margins = Rect(24.dp(context), 0, 24.dp(context), 0)
            root.addView(titleLayout, linearParams(wrapContent, wrapContent, margins = margins))
        }

        contentScroll = ScrollView(context).apply {

            contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL

                if (message != null) {
                    messageView = TextView(context).apply {
                        setTextColor(appTheme.getColor(key_dialogTextPrimary))
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                        gravity = LEFT or TOP
                        if (message.isEmpty()) {
                            visibility = View.GONE
                        } else {
                            visibility = View.VISIBLE
                            text = message
                        }
                    }

                    val margins = Rect(
                        24.dp(context),
                        0,
                        24.dp(context),
                        if (customView != null || items != null) customViewOffset else 0
                    )
                    addView(messageView, linearParams(matchParent, wrapContent, margins = margins))
                }

                if (items != null) {
                    for (i in items.indices) {
                        val cell = AlertDialogCell(context, appTheme).apply {
                            setTextAndIcon(items[i], itemIcons?.get(i) ?: 0)
                            tag = i
                            setOnClickListener { v: View ->
                                onClickListener?.onClick(this@AlertDialog, v.tag as Int)
                                dismiss()
                            }
                        }
                        addView(cell, linearParams(matchParent, 50.dp(context)))
                        itemViews.add(cell)
                    }
                }
            }

            addView(contentLayout, frameParams(matchParent, wrapContent))
        }
        root.addView(contentScroll, frameParams(matchParent, wrapContent))

        if (buttonPositiveText != null || buttonNegativeText != null || buttonNeutralText != null) {

            if (!verticalButtons) {
                var buttonsWidth = 0f
                val paint = TextPaint().apply { textSize = 14f.dp(context) }
                if (buttonPositiveText != null) {
                    buttonsWidth += paint.measureText(buttonPositiveText) + 10.dp(context)
                }
                if (buttonNegativeText != null) {
                    buttonsWidth += paint.measureText(buttonNegativeText) + 10.dp(context)
                }
                if (buttonNeutralText != null) {
                    buttonsWidth += paint.measureText(buttonNeutralText) + 10.dp(context)
                }
                if (buttonsWidth > 320.dp(context)) {
                    verticalButtons = true
                }
            }
            buttonsLayout = if (verticalButtons) {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
            } else {
                object : FrameLayout(context) {

                    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                        val count = childCount
                        var positiveButton: View? = null
                        val width = right - left
                        for (a in 0 until count) {
                            val child = getChildAt(a)
                            when (child.tag as Int) {
                                BUTTON_POSITIVE -> {
                                    positiveButton = child
                                    child.layout(
                                        width - paddingRight - child.measuredWidth,
                                        paddingTop, width - paddingRight, paddingTop + child.measuredHeight
                                    )
                                }
                                BUTTON_NEGATIVE -> {
                                    var x = width - paddingRight - child.measuredWidth
                                    if (positiveButton != null) {
                                        x -= positiveButton.measuredWidth + 8.dp(context)
                                    }
                                    child.layout(
                                        x,
                                        paddingTop,
                                        x + child.measuredWidth,
                                        paddingTop + child.measuredHeight
                                    )
                                }
                                BUTTON_NEUTRAL -> {
                                    child.layout(
                                        paddingLeft,
                                        paddingTop,
                                        paddingLeft + child.measuredWidth,
                                        paddingTop + child.measuredHeight
                                    )
                                }
                            }
                        }
                    }

                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                        var totalWidth = 0
                        val availableWidth = measuredWidth - paddingLeft - paddingRight
                        val count = childCount
                        for (a in 0 until count) {
                            val child = getChildAt(a)
                            if (child is TextView && child.getTag() != null) {
                                totalWidth += child.getMeasuredWidth()
                            }
                        }
                        if (totalWidth > availableWidth) {
                            val negative = findViewWithTag<View>(BUTTON_NEGATIVE)
                            val neutral = findViewWithTag<View>(BUTTON_NEUTRAL)
                            if (negative != null && neutral != null) {
                                if (negative.measuredWidth < neutral.measuredWidth) {
                                    neutral.measure(
                                        MeasureSpec.makeMeasureSpec(
                                            neutral.measuredWidth - (totalWidth - availableWidth),
                                            MeasureSpec.EXACTLY
                                        ),
                                        MeasureSpec.makeMeasureSpec(
                                            neutral.measuredHeight,
                                            MeasureSpec.EXACTLY
                                        )
                                    )
                                } else {
                                    negative.measure(
                                        MeasureSpec.makeMeasureSpec(
                                            negative.measuredWidth - (totalWidth - availableWidth),
                                            MeasureSpec.EXACTLY
                                        ),
                                        MeasureSpec.makeMeasureSpec(
                                            negative.measuredHeight,
                                            MeasureSpec.EXACTLY
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            buttonsLayout?.apply {
                val padding = 8.dp(context)
                setPadding(padding, padding, padding, padding)

                if (buttonPositiveText != null) {
                    val button = AlertDialogButton(context).apply {
                        tag = BUTTON_POSITIVE
                        text = buttonPositiveText
                        if (isDangerDialog) {
                            setTextColor(appTheme.getColor(key_dialogButtonDanger))
                        } else {
                            setTextColor(appTheme.getColor(key_dialogButton))
                        }

                        setOnClickListener {
                            onPositiveButtonListener?.onClick(this@AlertDialog, BUTTON_POSITIVE)

                            if (dismissDialogByButtons) dismiss()
                        }
                    }

                    if (verticalButtons) {
                        addView(button, linearParams(wrapContent, 36.dp(context), RIGHT))
                    } else {
                        addView(button, frameParams(wrapContent, 36.dp(context), TOP or RIGHT))
                    }
                }
                if (buttonNegativeText != null) {
                    val button = AlertDialogButton(context).apply {
                        tag = BUTTON_NEGATIVE
                        text = buttonNegativeText
                        setTextColor(appTheme.getColor(key_dialogButton))

                        setOnClickListener {
                            onNegativeButtonListener?.onClick(this@AlertDialog, BUTTON_NEGATIVE)

                            if (dismissDialogByButtons) dismiss()
                        }
                    }
                    if (verticalButtons) {
                        addView(button, 0, linearParams(wrapContent, 36.dp(context), RIGHT))
                    } else {
                        addView(button, frameParams(wrapContent, 36.dp(context), TOP or RIGHT))
                    }
                }
                if (buttonNeutralText != null) {
                    val button = AlertDialogButton(context).apply {
                        tag = BUTTON_NEUTRAL
                        text = buttonNeutralText
                        setTextColor(appTheme.getColor(key_dialogButton))

                        setOnClickListener {
                            onNeutralButtonListener?.onClick(this@AlertDialog, BUTTON_NEUTRAL)

                            if (dismissDialogByButtons) dismiss()
                        }
                    }
                    if (verticalButtons) {
                        addView(button, 1, linearParams(wrapContent, 36.dp(context), RIGHT))
                    } else {
                        addView(button, frameParams(wrapContent, 36.dp(context), TOP or LEFT))
                    }
                }
                if (verticalButtons) {
                    for (i in 1 until childCount) {
                        (getChildAt(i).layoutParams as ViewGroup.MarginLayoutParams).topMargin = 6.dp(context)
                    }
                }
            }

            root.addView(buttonsLayout, linearParams(matchParent, 52.dp(context)))
        }

    }

    override fun dismiss() {
        onDismissListener2?.onDismiss(this)

        try {
            super.dismiss()
        } catch (ignore: Throwable) {
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        buttonBackListener?.onClick(this@AlertDialog, BUTTON_NEGATIVE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {

    }


    override fun invalidateDrawable(who: Drawable) {
        contentLayout?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, delay: Long) {
        contentLayout?.postDelayed(what, delay)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        contentLayout?.removeCallbacks(what)
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener): AlertDialog {
        onDismissListener2 = listener
        return this
    }

    fun setOnClickListener(listener: DialogInterface.OnClickListener): AlertDialog {
        onClickListener = listener
        return this
    }

    fun onClickPositive(listener: DialogInterface.OnClickListener): AlertDialog {
        onPositiveButtonListener = listener
        return this
    }

    fun onClickNegative(listener: DialogInterface.OnClickListener): AlertDialog {
        onNegativeButtonListener = listener
        return this
    }

    fun onClickNeutral(listener: DialogInterface.OnClickListener): AlertDialog {
        onNeutralButtonListener = listener
        return this
    }

    @SuppressLint("ViewConstructor")
    class AlertDialogCell(context: Context, appTheme: Theme) : FrameLayout(context) {
        private val textView = TextView(context).apply {
            setLines(1)
            isSingleLine = true
            gravity = CENTER_VERTICAL or LEFT
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(appTheme.getColor(key_dialogTextPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        }
        private val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            colorFilter = appTheme.getTint(key_dialogIcon)
        }

        init {
            background = (appTheme.getRippleRect(key_dialogListRipple))
            setPadding(23.dp(context), 0, 23.dp(context), 0)

            addView(
                imageView,
                frameParams(wrapContent, 48.dp(context), CENTER_VERTICAL or RIGHT)
            )

            addView(
                textView,
                frameParams(matchParent, 48.dp(context), LEFT or CENTER_VERTICAL)
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(48.dp(context), MeasureSpec.EXACTLY))
        }

        fun setTextColor(color: Int) {
            textView.setTextColor(color)
        }

        fun setGravity(gravity: Int) {
            textView.gravity = gravity
        }

        fun setTextAndIcon(text: CharSequence?, icon: Int) {
            textView.text = text
            if (icon != 0) {
                imageView.setImageResource(icon)
                imageView.visibility = VISIBLE
                textView.setPadding(0, 0, 56.dp(context), 0)
            } else {
                imageView.visibility = INVISIBLE
                textView.setPadding(0, 0, 0, 0)
            }
        }
    }

    @SuppressLint("ViewConstructor")
    class AlertDialogButton(
        context: Context
    ) : TextView(context) {
        init {
            setPadding(10.dp(context), 0, 10.dp(context), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            typeface = roboto_semibold
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 64.dp(context)
            gravity = CENTER
            isSingleLine = true
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            alpha = if (enabled) 1.0f else 0.5f
        }

        override fun setTextColor(color: Int) {
            super.setTextColor(color)

            val rippleColor = ColorStateList(
                arrayOf(StateSet.WILD_CARD),
                intArrayOf(color and 0x00ffffff or 0x19000000)
            )
            val radius = 4f.dp(context)
            val corners = FloatArray(8) { radius }
            val shape = RoundRectShape(corners, null, null)
            val mask = ShapeDrawable(shape)

            background = RippleDrawable(rippleColor, null, mask)
        }

        override fun setText(text: CharSequence, type: BufferType) {
            super.setText(text.toString().uppercase(), type)
            contentDescription = text
        }
    }
}