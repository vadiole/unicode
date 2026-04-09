package vadiole.unicode.ui.details

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.unicodeStorage
import vadiole.unicode.UnicodeApp.Companion.userConfig
import vadiole.unicode.data.CharObj
import vadiole.unicode.data.CodePoint
import vadiole.unicode.ui.common.ActionCell
import vadiole.unicode.ui.common.CharInfoView
import vadiole.unicode.ui.common.CharTextView
import vadiole.unicode.ui.common.Screen
import vadiole.unicode.ui.common.SpacerDrawable
import vadiole.unicode.ui.common.SquircleDrawable
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.roboto_regular
import vadiole.unicode.ui.common.roboto_semibold
import vadiole.unicode.ui.common.setLineHeightX
import vadiole.unicode.ui.common.share
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.extension.getDividerPaint
import vadiole.unicode.ui.extension.onClick
import vadiole.unicode.ui.extension.onLongClick
import vadiole.unicode.ui.extension.setPadding

class DetailsSheet(
    context: Context,
    private val delegate: Delegate,
) : Screen(context) {
    interface Delegate {
        fun findInTable(codePoint: CodePoint)
    }

    private var charObj: CharObj? = null
    private val screenPadding = 20.dp(context)
    private val verticalPadding = 10.dp(context)
    private val backgroundDrawable = SquircleDrawable(
        cornerRadius = 0,
        topLeftRadius = 20.dp(context),
        topRightRadius = 20.dp(context),
    )
    private val backgroundPaint = Paint()
    private var vertical = 0
    private val titleHeight = 21.dp(context)
    private val title = TextView(context).apply(fun TextView.() {
        layoutParams = frameParams(matchParent, titleHeight, gravity = Gravity.TOP)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        setLineHeightX(21.dp(context))
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        vertical += titleHeight
        ellipsize = TextUtils.TruncateAt.END
        typeface = roboto_semibold
        letterSpacing = 0.03f
        isSingleLine = true
        onLongClick = {
            charObj?.let { value ->
                val name = value.name
                context.toClipboard("Unicode", name)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, name), LENGTH_SHORT).show()
                }
            }
        }
    })
    private val subtitleHeight = 18.dp(context)
    private val subtitle = TextView(context).apply(fun TextView.() {
        layoutParams = frameParams(matchParent, subtitleHeight, gravity = Gravity.TOP, marginTop = vertical)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setLineHeightX(18.dp(context))
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        textAlignment = TEXT_ALIGNMENT_CENTER
        vertical += subtitleHeight + verticalPadding
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        typeface = roboto_regular
        letterSpacing = 0.02f
        isSingleLine = true
    })
    private var divider1PositionY = 2f * screenPadding + titleHeight + subtitleHeight
    private val charViewHeight = 200.dp(context)
    private val charView = CharTextView(context).apply {
        vertical += verticalPadding
        layoutParams = frameParams(matchParent, charViewHeight, gravity = Gravity.TOP, marginTop = vertical)
        vertical += charViewHeight
        textSize = 100f.dp(context)
        onLongClick = {
            charObj?.let { value ->
                val char = value.char
                context.toClipboard("Unicode", char)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, char), LENGTH_SHORT).show()
                }
            }
        }
    }
    private val zoomMatrix = Matrix()
    private val tmpMatrixValues = FloatArray(9)
    private val identityValues = FloatArray(9).also { Matrix().getValues(it) }
    private val snapBackStartValues = FloatArray(9)
    private var pointerId0 = MotionEvent.INVALID_POINTER_ID
    private var pointerId1 = MotionEvent.INVALID_POINTER_ID
    private var prevSpan = 0f
    private var prevAngle = 0f
    private var prevFocusX = 0f
    private var prevFocusY = 0f
    private var isZooming = false
    private var snapBackAnim: ValueAnimator? = null
    private val minSpan = 10.dp(context).toFloat()
    private companion object {
        const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
        const val TWO_PI = (2.0 * Math.PI).toFloat()
        val PI_F = Math.PI.toFloat()
    }
    private val infoViewHeight = 56.dp(context)
    private val infoViews = List(4) {
        CharInfoView(context).apply {
            layoutParams = linearParams(matchParent, infoViewHeight, weight = 1f)
            onLongClick = {
                charObj?.let { value ->
                    val info = value.infoValues[it]
                    context.toClipboard("Unicode", info)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, info), LENGTH_SHORT).show()
                    }
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
    private val actionViewInTable = ActionCell(context, context.getString(R.string.details_find_in_table)).apply {
        layoutParams = frameParams(matchParent, actionCellHeight, marginTop = vertical)
        setIcon(R.drawable.ic_find_in_table)
        vertical += actionCellHeight + 16.dp(context)
        onClick = {
            charObj?.let { value ->
                delegate.findInTable(CodePoint(value.codePointRaw))
            }
        }
    }

    private val actionCopy = ActionCell(context, context.getString(R.string.details_copy_to_clipboard), topItem = true).apply {
        layoutParams = frameParams(matchParent, actionCellHeight, marginTop = vertical)
        setIcon(R.drawable.ic_copy)
        vertical += actionCellHeight
        onClick = {
            charObj?.let { value ->
                val char = value.char
                context.toClipboard("Unicode", char)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard, char), LENGTH_SHORT).show()
                }
            }
        }
    }
    private var divider2PositionY = vertical.toFloat() + screenPadding
    private val actionShare = ActionCell(context, context.getString(R.string.details_share_link), bottomItem = true).apply {
        layoutParams = frameParams(matchParent, actionCellHeight, marginTop = vertical)
        setIcon(R.drawable.ic_link)
        vertical += actionCellHeight
        var canClick = true
        onClick = {
            if (canClick) {
                launch {
                    canClick = false
                    charObj?.let { value ->
                        context.share(value.getLink())
                    }
                    delay(500)
                    canClick = true
                }
            }
        }
        onLongClick = {
            charObj?.let { value ->
                context.toClipboard("Unicode", value.getLink())
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(R.string.toast_link_copied_to_clipboard), LENGTH_SHORT).show()
                }
            }
        }
    }

    init {
        backgroundDrawable.colors = this.context.getColorStateList(R.color.dialogBackground)
        backgroundPaint.color = this.context.getColor(R.color.dialogBackground)
        charView.textColor = this.context.getColor(R.color.windowTextPrimary)
        title.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        subtitle.setTextColor(this.context.getColor(R.color.windowTextSecondary))
        background = backgroundDrawable
        val height = vertical + screenPadding * 2 + 36.dp(context)
        layoutParams = frameParams(matchParent, height, gravity = Gravity.BOTTOM)
        clipChildren = false
        clipToPadding = false
        setPadding(screenPadding)
        setWillNotDraw(false)
        addView(title)
        addView(subtitle)
        addView(charView)
        addView(infoViewsContainer)
        addView(actionViewInTable)
        addView(actionCopy)
        addView(actionShare)
        charView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerId0 = event.getPointerId(0)
                    false
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    v.cancelLongPress()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    snapBackAnim?.cancel()
                    pointerId0 = event.getPointerId(0)
                    pointerId1 = event.getPointerId(1)
                    val x0 = event.getX(0)
                    val y0 = event.getY(0)
                    val x1 = event.getX(1)
                    val y1 = event.getY(1)
                    val dx = x1 - x0
                    val dy = y1 - y0
                    prevSpan = sqrt(dx * dx + dy * dy).coerceAtLeast(minSpan)
                    prevAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    prevFocusX = (x0 + x1) / 2f
                    prevFocusY = (y0 + y1) / 2f
                    isZooming = true
                    charView.translationZ = 1f
                    if (!userConfig.usedPinchToZoom) {
                        userConfig.usedPinchToZoom = true
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isZooming) return@setOnTouchListener false
                    val idx0 = event.findPointerIndex(pointerId0)
                    val idx1 = event.findPointerIndex(pointerId1)
                    if (idx0 < 0 || idx1 < 0) {
                        endZoomGesture()
                        return@setOnTouchListener true
                    }
                    val x0 = event.getX(idx0)
                    val y0 = event.getY(idx0)
                    val x1 = event.getX(idx1)
                    val y1 = event.getY(idx1)
                    val dx = x1 - x0
                    val dy = y1 - y0
                    val currSpan = sqrt(dx * dx + dy * dy).coerceAtLeast(minSpan)
                    val currAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    val currFocusX = (x0 + x1) / 2f
                    val currFocusY = (y0 + y1) / 2f
                    val dScale = currSpan / prevSpan
                    var dAngle = currAngle - prevAngle
                    if (dAngle > PI_F) dAngle -= TWO_PI
                    if (dAngle < -PI_F) dAngle += TWO_PI
                    val parentFocusX = charView.left + currFocusX
                    val parentFocusY = charView.top + currFocusY
                    zoomMatrix.postScale(dScale, dScale, parentFocusX, parentFocusY)
                    zoomMatrix.postRotate(
                        dAngle * RAD_TO_DEG,
                        parentFocusX,
                        parentFocusY,
                    )
                    zoomMatrix.postTranslate(currFocusX - prevFocusX, currFocusY - prevFocusY)
                    prevSpan = currSpan
                    prevAngle = currAngle
                    prevFocusX = currFocusX
                    prevFocusY = currFocusY
                    invalidate()
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val liftedId = event.getPointerId(event.actionIndex)
                    if (liftedId == pointerId0 || liftedId == pointerId1) {
                        endZoomGesture()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isZooming) {
                        endZoomGesture()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bottomInset = insets.navigationBars.bottom
            updateLayoutParams<LayoutParams> {
                this.height = height + bottomInset
            }
            insets
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (child === charView && !zoomMatrix.isIdentity) {
            val count = canvas.save()
            canvas.concat(zoomMatrix)
            val result = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(count)
            return result
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun endZoomGesture() {
        isZooming = false
        pointerId0 = MotionEvent.INVALID_POINTER_ID
        pointerId1 = MotionEvent.INVALID_POINTER_ID
        animateSnapBack()
    }

    private fun animateSnapBack() {
        snapBackAnim?.cancel()
        zoomMatrix.getValues(snapBackStartValues)
        snapBackAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 250
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                for (i in 0 until 9) {
                    tmpMatrixValues[i] = identityValues[i] + (snapBackStartValues[i] - identityValues[i]) * t
                }
                zoomMatrix.setValues(tmpMatrixValues)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    zoomMatrix.reset()
                    charView.translationZ = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private val infoNames: Array<String> = arrayOf(
        context.getString(R.string.details_info_code),
        context.getString(R.string.details_info_html),
        context.getString(R.string.details_info_css),
        context.getString(R.string.details_info_version),
    )
    fun bind(codePoint: CodePoint, abbreviations: Map<CodePoint, String>) = launch {
        val obj: CharObj = unicodeStorage.getCharObj(codePoint) ?: return@launch
        title.text = obj.name
        subtitle.text = obj.blockName
        charView.text = obj.char
        charView.abbreviation = abbreviations[codePoint]
        infoViews.forEachIndexed { index, infoView ->
            infoView.name = infoNames[index]
            infoView.value = obj.infoValues[index]
        }
        charObj = obj
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        snapBackAnim?.cancel()
        zoomMatrix.reset()
        charView.translationZ = 0f
    }

    private val dividerPaint = getDividerPaint()
    override fun draw(canvas: Canvas) {
        canvas.drawRect(
            0f, measuredHeight - 20f.dp(context), measuredWidth.toFloat(), measuredHeight * 200f,
            backgroundPaint
        )
        super.draw(canvas)
        canvas.drawLine(0f, divider1PositionY, measuredWidth.toFloat(), divider1PositionY, dividerPaint)
        canvas.drawLine(
            screenPadding.toFloat(),
            divider2PositionY,
            measuredWidth.toFloat() - screenPadding, divider2PositionY,
            dividerPaint,
        )
    }
}
