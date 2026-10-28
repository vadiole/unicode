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
import androidx.core.view.updatePadding
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
import vadiole.unicode.ui.common.TEXT_SECONDARY
import vadiole.unicode.ui.common.TEXT_TITLE
import vadiole.unicode.ui.common.dp
import vadiole.unicode.ui.common.frameParams
import vadiole.unicode.ui.common.linearParams
import vadiole.unicode.ui.common.matchParent
import vadiole.unicode.ui.common.mono_regular
import vadiole.unicode.ui.common.mono_semibold
import vadiole.unicode.ui.common.navigationBars
import vadiole.unicode.ui.common.share
import vadiole.unicode.ui.common.toClipboard
import vadiole.unicode.ui.common.wrapContent
import vadiole.unicode.ui.extension.onClick
import vadiole.unicode.ui.extension.onLongClick

class DetailsSheet(
    context: Context,
    private val delegate: Delegate,
) : Screen(context) {
    interface Delegate {
        fun findInTable(codePoint: CodePoint)
        fun addToDock(codePoint: CodePoint)
    }

    private var charObj: CharObj? = null
    private val screenPadding = 20.dp(context)
    private val verticalPadding = 10.dp(context)
    private val titleHeight = 21.dp(context)
    private val subtitleHeight = 18.dp(context)
    private val charViewHeight = 200.dp(context)
    private val actionGroupGap = 16.dp(context)
    private val bottomBuffer = 36.dp(context)
    private val hairlinePx = 1

    private val backgroundDrawable = SquircleDrawable(
        cornerRadius = 0,
        topLeftRadius = 20.dp(context),
        topRightRadius = 20.dp(context),
    )
    private val backgroundPaint = Paint()

    private val title = TextView(context).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_TITLE)
        minHeight = titleHeight
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        typeface = mono_semibold
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
    }

    private val subtitle = TextView(context).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SECONDARY)
        minHeight = subtitleHeight
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        textAlignment = TEXT_ALIGNMENT_CENTER
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        typeface = mono_regular
        isSingleLine = true
    }

    private val topBlock = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = linearParams(matchParent, wrapContent)
        setPadding(screenPadding, screenPadding, screenPadding, 0)
        addView(title)
        addView(subtitle)
    }

    private val divider1 = View(context).apply {
        layoutParams = linearParams(matchParent, hairlinePx, marginTop = screenPadding)
        setBackgroundColor(context.getColor(R.color.windowDivider))
    }

    private val charView = CharTextView(context).apply {
        layoutParams = linearParams(matchParent, charViewHeight)
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

    private val infoViews = List(4) {
        CharInfoView(context).apply {
            layoutParams = linearParams(matchParent, wrapContent, weight = 1f)
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
        layoutParams = linearParams(matchParent, wrapContent)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = SpacerDrawable(width = 8.dp(context))
        infoViews.forEach(::addView)
    }

    private val actionAdd = ActionCell(context, context.getString(R.string.details_add), topItem = true).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setIcon(R.drawable.ic_add)
        onClick = {
            charObj?.let { value ->
                delegate.addToDock(CodePoint(value.codePointRaw))
            }
        }
    }

    private val actionViewInTable = ActionCell(context, context.getString(R.string.details_find_in_table), bottomItem = true).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setIcon(R.drawable.ic_find_in_table)
        onClick = {
            charObj?.let { value ->
                delegate.findInTable(CodePoint(value.codePointRaw))
            }
        }
    }

    private val actionCopy = ActionCell(context, context.getString(R.string.details_copy_to_clipboard), topItem = true).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setIcon(R.drawable.ic_copy)
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

    private val actionShare = ActionCell(context, context.getString(R.string.details_share_link), bottomItem = true).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setIcon(R.drawable.ic_link)
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

    private val actionFindInTable = ActionCell(context, context.getString(R.string.details_find_in_table)).apply {
        layoutParams = linearParams(matchParent, wrapContent)
        setIcon(R.drawable.ic_find_in_table)
        visibility = GONE
        onClick = {
            charObj?.let { value ->
                delegate.findInTable(CodePoint(value.codePointRaw))
            }
        }
    }

    private val divider2 = View(context).apply {
        layoutParams = linearParams(matchParent, hairlinePx)
        setBackgroundColor(context.getColor(R.color.windowDivider))
    }

    private val divider3 = View(context).apply {
        layoutParams = linearParams(matchParent, hairlinePx)
        setBackgroundColor(context.getColor(R.color.windowDivider))
    }

    private val tableActionGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = linearParams(matchParent, wrapContent, marginTop = verticalPadding * 3)
        addView(actionAdd)
        addView(divider3)
        addView(actionViewInTable)
        addView(actionFindInTable)
    }

    fun setDockEnabled(enabled: Boolean) {
        val dockVisibility = if (enabled) VISIBLE else GONE
        actionAdd.visibility = dockVisibility
        actionViewInTable.visibility = dockVisibility
        divider3.visibility = dockVisibility
        actionFindInTable.visibility = if (enabled) GONE else VISIBLE
    }

    private val actionGroup = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = linearParams(matchParent, wrapContent, marginTop = actionGroupGap)
        addView(actionCopy)
        addView(divider2)
        addView(actionShare)
    }

    private val bottomBlock = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = linearParams(matchParent, wrapContent)
        setPadding(screenPadding, 0, screenPadding, screenPadding + bottomBuffer)
        addView(infoViewsContainer)
        addView(tableActionGroup)
        addView(actionGroup)
    }

    private val content = object : LinearLayout(context) {
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
    }.apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = frameParams(matchParent, wrapContent)
        addView(topBlock)
        addView(divider1)
        addView(charView)
        addView(bottomBlock)
    }

    init {
        backgroundDrawable.colors = this.context.getColorStateList(R.color.layer0)
        backgroundPaint.color = this.context.getColor(R.color.layer0)
        charView.textColor = this.context.getColor(R.color.windowTextPrimary)
        title.setTextColor(this.context.getColor(R.color.windowTextPrimary))
        subtitle.setTextColor(this.context.getColor(R.color.windowTextSecondary))
        background = backgroundDrawable
        layoutParams = frameParams(matchParent, wrapContent, gravity = Gravity.BOTTOM)
        clipChildren = false
        setWillNotDraw(false)
        addView(content)
        charView.setOnTouchListener(::handleZoomTouch)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bottomBlock.updatePadding(bottom = screenPadding + bottomBuffer + insets.navigationBars.bottom)
            insets
        }
    }

    private fun handleZoomTouch(v: View, event: MotionEvent): Boolean = when (event.actionMasked) {
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
            if (!isZooming) {
                false
            } else {
                val idx0 = event.findPointerIndex(pointerId0)
                val idx1 = event.findPointerIndex(pointerId1)
                if (idx0 < 0 || idx1 < 0) {
                    endZoomGesture()
                } else {
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
                    zoomMatrix.postRotate(dAngle * RAD_TO_DEG, parentFocusX, parentFocusY)
                    zoomMatrix.postTranslate(currFocusX - prevFocusX, currFocusY - prevFocusY)
                    prevSpan = currSpan
                    prevAngle = currAngle
                    prevFocusX = currFocusX
                    prevFocusY = currFocusY
                    content.invalidate()
                }
                true
            }
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
                content.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    zoomMatrix.reset()
                    charView.translationZ = 0f
                    content.invalidate()
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

    override fun draw(canvas: Canvas) {
        canvas.drawRect(
            0f, measuredHeight - 20f.dp(context), measuredWidth.toFloat(), measuredHeight * 200f,
            backgroundPaint
        )
        super.draw(canvas)
    }
}
