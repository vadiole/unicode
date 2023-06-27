package vadiole.unicode.ui.table.selector

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LayoutAnimationController
import android.view.animation.PathInterpolator
import android.view.animation.ScaleAnimation
import androidx.core.animation.addListener
import androidx.core.graphics.drawable.updateBounds
import androidx.recyclerview.widget.LinearLayoutManager
import vadiole.unicode.R
import vadiole.unicode.UnicodeApp.Companion.themeManager
import vadiole.unicode.data.Block
import vadiole.unicode.ui.common.CollectionItemDecoration
import vadiole.unicode.ui.common.CollectionView
import vadiole.unicode.ui.common.Squircle
import vadiole.unicode.ui.theme.ThemeDelegate
import vadiole.unicode.ui.theme.key_dialogSurface
import vadiole.unicode.utils.extension.dp

class BlockSelectorView(
    context: Context,
    private val blocks: List<Block>,
    private val delegate: Delegate,
) : CollectionView(context), ThemeDelegate {
    interface Delegate {
        fun onBlockSelected(block: Block)
    }

    private val squircle = Squircle().apply {
        cornerRadiusPx = 12.dp(context)
    }
    private val backgroundDrawable = ColorDrawable()

    private val blockSelectorLayoutManager = LinearLayoutManager(context).apply {
        orientation = LinearLayoutManager.VERTICAL
    }

    private val adapter = object : Adapter() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell {
            return Cell(BlockSelectorCell(context))
        }

        override fun onBindViewHolder(holder: Cell, position: Int) {
            val block = blocks[position]
            val cell = holder.itemView as BlockSelectorCell
            cell.text = block.name
            cell.setOnClickListener {
                delegate.onBlockSelected(block)
            }
        }

        override fun getItemCount(): Int {
            return blocks.size
        }
    }

    private val itemDecoration = CollectionItemDecoration(leftPadding = 14f.dp(context))
    private var showAnimator: ValueAnimator? = null
    private val anchorDrawable = context.getDrawable(R.drawable.ic_anchor)!!.mutate()
    private val topMargin = anchorDrawable.intrinsicHeight - 4.dp(context)

    init {
        themeManager.observe(this)
        background = backgroundDrawable
        recycledViewPool.setMaxRecycledViews(0, 32)
        layoutManager = blockSelectorLayoutManager
        setItemViewCacheSize(8)
        setAdapter(adapter)
        addItemDecoration(itemDecoration)
        squircle.attach(this)
        setPadding(0, 8.dp(context) + topMargin, 0, 8.dp(context))
        layoutAnimation = LayoutAnimationController(
            AnimationSet(true).apply {
                interpolator = showInterpolator
                addAnimation(
                    ScaleAnimation(
                        0.95f, 1f, 0.95f, 1f, Animation.RELATIVE_TO_SELF, 0.1f, Animation.RELATIVE_TO_SELF, 0.5f
                    ).apply {
                        duration = 300
                    }
                )
                addAnimation(
                    AlphaAnimation(
                        0.7f, 1f
                    ).apply {
                        duration = 300
                    }
                )
            },
        ).apply {
            order = LayoutAnimationController.ORDER_NORMAL
            delay = 0.04f
        }
        adapter.notifyDataSetChanged()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(calculateWidth(), MeasureSpec.EXACTLY)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(calculateHeight(), MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val centerX = w * 0.5f
        anchorDrawable.setBounds(
            centerX.toInt() - anchorDrawable.intrinsicWidth / 2,
            0,
            centerX.toInt() + anchorDrawable.intrinsicWidth / 2,
            anchorDrawable.intrinsicHeight
        )
    }

    fun calculateWidth(): Int {
        val displayWidth = context.resources.displayMetrics.widthPixels
        return (500 - 16).dp(context).coerceAtMost((displayWidth * .95f).toInt())
    }

    fun calculateHeight(): Int {
        val displayHeight = context.resources.displayMetrics.heightPixels
        return (700 - 16 - 64).dp(context).coerceAtMost((displayHeight * .75f).toInt())
    }

    override fun draw(c: Canvas) {
        drawAnchor(c)
        squircle.clip(c) {
            super.draw(c)
        }
    }

    private fun drawAnchor(c: Canvas) {
        anchorDrawable.draw(c)
    }

    override fun applyTheme() {
        backgroundDrawable.color = themeManager.getColor(key_dialogSurface)
        anchorDrawable.setTint(themeManager.getColor(key_dialogSurface))
        invalidate()
    }

    fun show() {
        val lastProgress = showAnimator?.animatedValue as? Float ?: 0f
        showAnimator?.cancel()
        showAnimator = ValueAnimator.ofFloat(lastProgress, 1f).apply {
            duration = 300
            interpolator = showInterpolator
            addUpdateListener { animator ->
                setShowProgress(animator.animatedValue as Float, dismiss = false)
            }
            start()
        }
    }

    fun dismiss(onEnd: () -> Unit) {
        val currentProgress = showAnimator?.animatedValue as? Float ?: 1f
        showAnimator?.cancel()
        showAnimator = ValueAnimator.ofFloat(currentProgress, 0f).apply {
            duration = 300
            interpolator = showInterpolator
            addUpdateListener { progress ->
                setShowProgress(progress.animatedValue as Float, true)
            }
            addListener(onEnd = { onEnd() })
            start()
        }
    }

    private fun setShowProgress(progress: Float, dismiss: Boolean) {
        val actualProgress = 1f - progress
        alpha = progress
        val translationModifier = if (dismiss) -1 else -1
        val clipModifier = if (dismiss) 0.1f else 1f
        translationY = translationModifier * 8.dp(context).toFloat() * actualProgress
        val horizontalOffset = (right - left) * 0.4f * actualProgress * clipModifier
        val topOffset = 12.dp(context).toFloat() * actualProgress
        val bottomOffset = (bottom - top) * 0.9f * actualProgress * clipModifier
        val left = left + horizontalOffset
        val right = right - horizontalOffset
        val top = top + topOffset + topMargin
        val bottom = bottom - bottomOffset
        squircle.attach(this, left, top, right, bottom)
        anchorDrawable.updateBounds(top = topOffset.toInt(), bottom = topOffset.toInt() + anchorDrawable.intrinsicHeight)
        invalidate()
    }

    companion object {
        private val showInterpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }
}
