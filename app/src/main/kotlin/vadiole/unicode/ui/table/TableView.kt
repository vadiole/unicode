package vadiole.unicode.ui.table

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.showsLayoutBounds
import vadiole.unicode.ui.common.dp

class TableView(
    context: Context,
    private val adapter: TableAdapter,
    private val spanCount: Int,
    private val delegate: Delegate,
) : CollectionView(context) {
    private val tableLayoutManager = object : LinearLayoutManager(context) {
        override fun calculateExtraLayoutSpace(state: State, extraLayoutSpace: IntArray) {
            val rowHeight = getChildAt(0)?.height ?: 0
            extraLayoutSpace[0] = rowHeight
            extraLayoutSpace[1] = rowHeight
        }
    }

    init {
        recycledViewPool.setMaxRecycledViews(0, spanCount * 6)
        layoutManager = tableLayoutManager
        setItemViewCacheSize(spanCount * 2)
        setAdapter(adapter)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        val block = currentBlock()
        if (block != null) {
            delegate.onBlockChanged(block.name)
        }
        delegate.onScrollProgressChanged(getScrollProgress())
        if (dy != 0) trackSpeed(dy)
        if (showsLayoutBounds) invalidate()
    }

    private var rowsPerSecond = 0f
    private var lastScrollTime = 0L
    private var isAdjustingScale = false
    private val refineEmoji = object : Runnable {
        override fun run() {
            if (!canSharpen() || isSharpening || !isWindowShown) return
            for (pass in 0..1) {
                for (index in 0 until childCount) {
                    val row = getChildAt(index) as CharRow
                    if (!row.hasEmojiSprites || row.spriteTextSize == row.charSize || isRowVisible(row) != (pass == 0)) continue
                    val sharpener = sharpener ?: EmojiSharpener().also { sharpener = it }
                    isSharpening = true
                    sharpeningRow = row
                    if (showsLayoutBounds) invalidate()
                    row.sharpenEmoji(row.charSize, sharpener, sharpened)
                    return
                }
            }
        }
    }
    private val sharpened = Runnable {
        isSharpening = false
        sharpeningRow = null
        if (showsLayoutBounds) invalidate()
        scheduleRefineEmoji(0L)
    }
    private var sharpener: EmojiSharpener? = null
    private var isWindowShown = true
    private var isSharpening = false
    private var sharpeningRow: CharRow? = null
    private val readoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12f.dp(context)
        typeface = Typeface.MONOSPACE
    }
    private val readoutBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
    private val readoutRect = RectF()
    private val readout = StringBuilder()
    private val readoutPadding = 6f.dp(context)
    private val readoutMargin = 8f.dp(context)
    private val readoutRadius = 6f.dp(context)

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(refineEmoji)
        sharpener?.release()
        sharpener = null
        isSharpening = false
        sharpeningRow = null
    }

    private fun canSharpen() = !isAdjustingScale && (scrollState == SCROLL_STATE_IDLE || rowsPerSecond <= FAST_ROWS_PER_SECOND)

    private fun isRowVisible(row: View) = row.bottom > paddingTop && row.top < height - paddingBottom

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val shown = visibility == VISIBLE
        if (shown == isWindowShown) return
        isWindowShown = shown
        if (shown) {
            for (index in 0 until childCount) (getChildAt(index) as CharRow).restoreSprites()
            scheduleRefineEmoji(SHARPEN_DELAY)
        } else {
            removeCallbacks(refineEmoji)
            for (index in 0 until childCount) (getChildAt(index) as CharRow).releaseSprites()
            EmojiGlyphs.clearFree()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (showsLayoutBounds) drawReadout(canvas)
    }

    private fun drawReadout(canvas: Canvas) {
        var preview = 0
        var sharp = 0
        var scaled = 0
        for (index in 0 until childCount) {
            val row = getChildAt(index) as CharRow
            if (!row.hasEmojiSprites || !isRowVisible(row)) continue
            when {
                row.isSpritePreview -> preview++
                row.spriteTextSize == row.charSize -> sharp++
                else -> scaled++
            }
        }
        readout.setLength(0)
        readout.append((rowsPerSecond + 0.5f).toInt()).append(if (canSharpen()) " rows/s on" else " rows/s off")
            .append("  low ").append(preview).append("  sharp ").append(sharp)
            .append("  scaled ").append(scaled).append("  wait ").append(if (sharpeningRow != null) 1 else 0)
        val width = readoutPaint.measureText(readout, 0, readout.length)
        val left = paddingLeft + readoutMargin
        val top = paddingTop + readoutMargin
        readoutRect.set(left, top, left + width + readoutPadding * 2, top + readoutPaint.fontSpacing + readoutPadding * 2)
        canvas.drawRoundRect(readoutRect, readoutRadius, readoutRadius, readoutBackground)
        canvas.drawText(readout, 0, readout.length, left + readoutPadding, top + readoutPadding - readoutPaint.ascent(), readoutPaint)
    }

    private fun scheduleRefineEmoji(delay: Long) {
        removeCallbacks(refineEmoji)
        if (delay > 0L) postDelayed(refineEmoji, delay) else postOnAnimation(refineEmoji)
    }

    fun setScaleAdjusting(adjusting: Boolean) {
        if (isAdjustingScale == adjusting) return
        isAdjustingScale = adjusting
        if (showsLayoutBounds) invalidate()
        if (!adjusting) scheduleRefineEmoji(0L)
    }

    fun onCharacterScaleChanged() {
        if (!isAdjustingScale) scheduleRefineEmoji(0L)
    }

    override fun onScrollStateChanged(state: Int) {
        if (state != SCROLL_STATE_IDLE) return
        rowsPerSecond = 0f
        if (showsLayoutBounds) invalidate()
        scheduleRefineEmoji(SHARPEN_DELAY)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (scrollState == SCROLL_STATE_IDLE && !isAdjustingScale) scheduleRefineEmoji(SHARPEN_DELAY)
    }

    private fun trackSpeed(dy: Int) {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastScrollTime
        lastScrollTime = now
        val rowHeight = getChildAt(0)?.height ?: 0
        if (elapsed > SPEED_WINDOW || rowHeight == 0) {
            rowsPerSecond = 0f
        } else if (elapsed > 0L) {
            rowsPerSecond += (abs(dy) * 1000f / rowHeight / elapsed - rowsPerSecond) * SPEED_SMOOTHING
        }
        if (scrollState != SCROLL_STATE_IDLE && rowsPerSecond <= FAST_ROWS_PER_SECOND) scheduleRefineEmoji(0L)
    }

    fun currentBlock() = adapter.getBlock(tableLayoutManager.findFirstVisibleItemPosition() * spanCount)

    fun getScrollProgress(): Float {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val offset = computeVerticalScrollOffset()
        val scrollable = range - extent
        if (scrollable <= 0) return 0f
        return (offset.toFloat() / scrollable).coerceIn(0f, 1f)
    }

    fun scrollToProgress(progress: Float, precisionLevel: Int) {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return
        if (precisionLevel == 2) {
            val range = computeVerticalScrollRange()
            val extent = computeVerticalScrollExtent()
            val scrollable = range - extent
            val rowHeight = range / itemCount
            if (scrollable > 0 && rowHeight > 0) {
                val targetPx = (scrollable * progress.coerceIn(0f, 1f)).toLong()
                val row = (targetPx / rowHeight).toInt().coerceAtMost(itemCount - 1)
                val intraRowOffset = (targetPx - row.toLong() * rowHeight).toInt()
                tableLayoutManager.scrollToPositionWithOffset(row, -intraRowOffset)
                return
            }
        }
        val targetPosition = (itemCount * progress).toInt().coerceIn(0, itemCount - 1)
        tableLayoutManager.scrollToPositionWithOffset(targetPosition, 0)
    }

    fun scrollToPositionInCenter(row: Int, indexInRow: Int) {
        val offset = measuredHeight / 2
        tableLayoutManager.scrollToPositionWithOffset(row, offset)
        doOnNextLayout {
            val cell = tableLayoutManager.findViewByPosition(row) as? CharRow ?: return@doOnNextLayout
            cell.highlightChar(indexInRow)
        }
    }

    fun scrollToPositionTop(row: Int, offset: Int = 0) {
        tableLayoutManager.scrollToPositionWithOffset(row, offset)
    }

    val firstVisibleRow: Int
        get() = tableLayoutManager.findFirstVisibleItemPosition()

    val firstVisibleRowOffset: Int
        get() = tableLayoutManager.findViewByPosition(firstVisibleRow)?.let { it.top - paddingTop } ?: 0

    private companion object {
        const val SPEED_WINDOW = 100L
        const val SPEED_SMOOTHING = 0.5f
        const val FAST_ROWS_PER_SECOND = 40f
        const val SHARPEN_DELAY = 67L
    }

    interface Delegate {
        fun onBlockChanged(name: String?)
        fun onScrollProgressChanged(progress: Float)
    }

}
