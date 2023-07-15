package vadiole.unicode.ui.common

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.view.View
import androidx.core.graphics.withClip
import kotlin.math.pow

class Squircle {
    var cornerRadiusPx = 0
        set(value) {
            if (field == value) return
            field = value
            tryRecalculatePath()
        }
    var skipTopLeft = false
        set(value) {
            if (field == value) return
            field = value
            tryRecalculatePath()
        }
    var skipTopRight = false
        set(value) {
            if (field == value) return
            field = value
            tryRecalculatePath()
        }
    var skipBottomLeft = false
        set(value) {
            if (field == value) return
            field = value
            tryRecalculatePath()
        }
    var skipBottomRight = false
        set(value) {
            if (field == value) return
            field = value
            tryRecalculatePath()
        }
    private var targetView: View? = null
    private val squirclePath = Path()
    private val cornerPath = Path()
    private val clippingPath = Path()
    private val mirrorMatrix = Matrix()

    private val layoutChangeListener: View.OnLayoutChangeListener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int,
        ) {
            if (top == oldTop && left == oldLeft && right == oldRight && bottom == oldBottom) return
            recalculatePath(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }
    }

    fun attach(view: View) {
        targetView = view
        view.addOnLayoutChangeListener(layoutChangeListener)
    }

    fun attach(
        view: View,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        targetView = view
        recalculatePath(left, top, right, bottom)
    }

    fun detach() {
        targetView?.removeOnLayoutChangeListener(layoutChangeListener)
        targetView = null
    }

    fun clip(canvas: Canvas, block: Canvas.() -> Unit) {
        canvas.withClip(squirclePath, block)
    }

    private fun tryRecalculatePath() {
        targetView?.run {
            recalculatePath(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }
    }

    private fun recalculatePath(left: Float, top: Float, right: Float, bottom: Float) {
        val centerX = (right + left) / 2f
        val centerY = (bottom + top) / 2f

        //  range (0..1), ios = 0.6
        val smooth = 0.6f
        val r = cornerRadiusPx
        //  a, b, c -> https://telegra.ph/file/a65d7a87521e9c75e7579.png
        val c = 0.2929f * r
        val b = (1.5f * (2f * c * c).pow(x = 1.5f) / (c * r))
        val a = r * (1 + smooth) - c - c - b
        val ab = a + b
        val cb = c + b
        val abc = a + b + c
        val abcc = a + b + c + c

        cornerPath.apply {
            rewind()
            moveTo(left, top)
            lineTo(left, top + abcc)
            rCubicTo(0f, -a, 0f, -ab, c, -abc)
            rCubicTo(c, -c, cb, -c, abc, -c)
            lineTo(left, top)
        }

        squirclePath.apply {
            rewind()
            addRect(left, top, right, bottom, Path.Direction.CW)
            mirrorParams.forEachIndexed { index, (scaleX, scaleY) ->
                if (isCornerSkipped(index)) return@forEachIndexed
                mirrorMatrix.setScale(scaleX, scaleY, centerX, centerY)
                cornerPath.transform(mirrorMatrix, clippingPath)
                op(clippingPath, Path.Op.DIFFERENCE)
            }
        }
    }

    private fun isCornerSkipped(cornerIndex: Int): Boolean = when (cornerIndex) {
        0 -> skipTopLeft
        1 -> skipTopRight
        2 -> skipBottomRight
        3 -> skipBottomLeft
        else -> error("Invalid corner index")
    }

    companion object {
        private val mirrorParams = arrayOf(1f to 1f, -1f to 1f, -1f to -1f, 1f to -1f)
    }
}
