package vadiole.unicode.ui.common

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.pow

/**
 * StateListDrawable with iOS-like rounded corners
 *
 * [Read more about Squircle](https://www.figma.com/blog/desperately-seeking-squircles)
 */
class SquircleDrawable(private val cornerRadius: Int) : Drawable() {
    var colors: ColorStateList = ColorStateList.valueOf(Color.DKGRAY)
        set(value) {
            field = value
            stateAnimator?.cancel()
            paint.color = value.getColorForState(state, value.defaultColor)
            invalidateSelf()
        }
    var skipTopLeft = false
    var skipTopRight = false
    var skipBottomLeft = false
    var skipBottomRight = false
    private var stateAnimator: Animator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun isStateful(): Boolean = true

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun onStateChange(state: IntArray): Boolean {
        val newColor = colors.getColorForState(state, paint.color)
        return if (stateAnimator?.isRunning == true || newColor != paint.color) {
            stateAnimator?.cancel()
            stateAnimator = ValueAnimator.ofArgb(paint.color, newColor).apply {
                duration = 120L
                addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    paint.color = color
                    invalidateSelf()
                }
                start()
                invalidateSelf()
            }
            true
        } else {
            false
        }
    }

    //  initialize squircle path
    @Suppress("SpellCheckingInspection")
    override fun onBoundsChange(bounds: Rect) {
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val radius = cornerRadius   //  corners radius in px
        val smooth = 0.6f           //  from 0 to 1, ios = 0.6
        //  a, b, c -> https://telegra.ph/file/a65d7a87521e9c75e7579.png
        val c = 0.2929f * radius
        val b = (1.5f * (2f * c * c).pow(x = 1.5f) / (c * radius))
        val a = radius * (1 + smooth) - c - c - b
        val ab = a + b
        val cb = c + b
        val abc = a + b + c
        val abcc = a + b + c + c
        with(path) {
            rewind()
            if (skipTopLeft) {
                moveTo(left, top)
            } else {
                moveTo(left, top + abcc)
                rCubicTo(0f, -a, 0f, -ab, c, -abc)
                rCubicTo(c, -c, cb, -c, abc, -c)
            }

            //  left top corner
            if (skipTopRight) {
                lineTo(right, top)
            } else {
                lineTo(right - abcc, top)
            }

            //  right top corner
            if (skipTopRight) {
                lineTo(right, bottom - abcc)
            } else {
                rCubicTo(a, 0f, ab, 0f, abc, c)
                rCubicTo(c, c, c, cb, c, abc)
            }
            if (skipBottomRight) {
                lineTo(right, bottom)
            } else {
                lineTo(right, bottom - abcc)
                rCubicTo(0f, a, 0f, ab, -c, abc)
                rCubicTo(-c, c, -cb, c, -abc, c)
            }

            //  right bottom corner
            if (skipBottomLeft) {
                lineTo(left, bottom)
            } else {
                lineTo(left + abcc, bottom)
            }

            //  left bottom corner
            if (skipBottomLeft) {
                lineTo(-c, -abc)
            } else {
                rCubicTo(-a, 0f, -ab, 0f, -abc, -c)
                rCubicTo(-c, -c, -c, -cb, -c, -abc)
            }
            close()
            fillType = Path.FillType.EVEN_ODD
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}