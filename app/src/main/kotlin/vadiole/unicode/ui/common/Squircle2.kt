package vadiole.unicode.ui.common

import android.graphics.Matrix
import android.graphics.Path
import kotlin.math.min
import kotlin.math.pow

class Squircle2(
    topLeftCornerRadius: Int,
    topRightCornerRadius: Int,
    bottomRightCornerRadius: Int,
    bottomLeftCornerRadius: Int,
) {

    constructor(cornerRadius: Int) : this(cornerRadius, cornerRadius, cornerRadius, cornerRadius)

    val path = Path()
    private val radii = floatArrayOf(
        topLeftCornerRadius.toFloat(),
        topRightCornerRadius.toFloat(),
        bottomRightCornerRadius.toFloat(),
        bottomLeftCornerRadius.toFloat()
    )
    private val cornerPath = Path()
    private val clippingPath = Path()
    private val mirrorMatrix = Matrix()

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        recalculatePath(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    private fun recalculatePath(left: Float, top: Float, right: Float, bottom: Float) {
        val centerX = (right + left) / 2f
        val centerY = (bottom + top) / 2f
        val width = right - left
        val height = bottom - top
        val smooth = 0.6f // range (0..1), ios = 0.6
        path.apply {
            rewind()
            addRect(left, top, right, bottom, Path.Direction.CW)
        }
        radii.forEachIndexed { index, cornerRadius ->
            if (cornerRadius <= 0) return@forEachIndexed
            if (index == 0 || cornerRadius != radii[index - 1]) {
                val maxRadius = min(width, height) / 2f
                val r = min(cornerRadius, maxRadius)
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
            }
            val (scaleX, scaleY) = mirrorParams[index]
            mirrorMatrix.setScale(scaleX, scaleY, centerX, centerY)
            cornerPath.transform(mirrorMatrix, clippingPath)
            path.op(clippingPath, Path.Op.DIFFERENCE)
        }
    }
}

private val mirrorParams = arrayOf(1f to 1f, -1f to 1f, -1f to -1f, 1f to -1f)
