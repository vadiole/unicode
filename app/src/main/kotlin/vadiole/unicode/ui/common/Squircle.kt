package vadiole.unicode.ui.common

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * [Source](https://github.com/vadiole/squircle/blob/main/SquircleDrawable.kt)
 */
fun Path.addSquircle(rect: RectF, radius: Float, smoothing: Float = 0.6f) {
    addSquircle(rect.left, rect.top, rect.right, rect.bottom, radius, radius, radius, radius, smoothing)
}

fun Path.addSquircle(rect: RectF, radii: FloatArray, smoothing: Float = 0.6f) {
    require(radii.size == 4 || radii.size == 8) { "radii must have 4 or 8 values" }
    val step = radii.size / 4
    addSquircle(rect.left, rect.top, rect.right, rect.bottom, radii[0], radii[step], radii[2 * step], radii[3 * step], smoothing)
}

private fun Path.addSquircle(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
) {
    require(smoothing in 0f..1f) { "smoothing must be from 0 to 1" }
    val width = right - left
    val height = bottom - top
    if (width <= 0f || height <= 0f) return
    val tl = topLeft.coerceAtLeast(0f)
    val tr = topRight.coerceAtLeast(0f)
    val br = bottomRight.coerceAtLeast(0f)
    val bl = bottomLeft.coerceAtLeast(0f)
    val scale = minOf(1f, fit(width, tl + tr), fit(height, tr + br), fit(width, br + bl), fit(height, bl + tl))
    val r0 = tl * scale
    val r1 = tr * scale
    val r2 = br * scale
    val r3 = bl * scale
    corner(0, left, top, r0, min(share(height, r0, r3), share(width, r0, r1)), smoothing)
    corner(1, right, top, r1, min(share(width, r1, r0), share(height, r1, r2)), smoothing)
    corner(2, right, bottom, r2, min(share(height, r2, r1), share(width, r2, r3)), smoothing)
    corner(3, left, bottom, r3, min(share(width, r3, r2), share(height, r3, r0)), smoothing)
    close()
}

private val IN_X = floatArrayOf(0f, 1f, 0f, -1f)
private val IN_Y = floatArrayOf(-1f, 0f, 1f, 0f)

private fun Path.corner(index: Int, cornerX: Float, cornerY: Float, radius: Float, budget: Float, smoothing: Float) {
    val ix = IN_X[index]
    val iy = IN_Y[index]
    val r = min(radius, budget)
    if (r <= 0f) {
        if (index == 0) moveTo(cornerX, cornerY) else lineTo(cornerX, cornerY)
        return
    }
    val s = min(smoothing, budget / r - 1f)
    val p = (1f + s) * r
    val angle = (PI / 4).toFloat() * s
    val arc = sqrt(2f) * r * sin((PI / 4).toFloat() - angle)
    val tangent = r * tan(angle / 2f)
    val c = tangent * cos(angle)
    val d = tangent * sin(angle)
    val b = (p - arc - c - d) / 3f
    val a = 2f * b
    val startX = cornerX - p * ix
    val startY = cornerY - p * iy
    val endX = cornerX - p * iy
    val endY = cornerY + p * ix
    if (index == 0) moveTo(startX, startY) else lineTo(startX, startY)
    if (s > 0f) {
        cubicTo(startX + a * ix, startY + a * iy, startX + (a + b) * ix, startY + (a + b) * iy, startX + (a + b + c) * ix - d * iy, startY + (a + b + c) * iy + d * ix)
    }
    if (s < 1f) {
        val centerX = cornerX - r * ix - r * iy
        val centerY = cornerY - r * iy + r * ix
        val start = 180f + 90f * index + angle * (180f / PI.toFloat())
        arcTo(centerX - r, centerY - r, centerX + r, centerY + r, start, 90f - 2f * angle * (180f / PI.toFloat()), false)
    }
    if (s > 0f) {
        cubicTo(endX + (a + b) * iy, endY - (a + b) * ix, endX + a * iy, endY - a * ix, endX, endY)
    }
}

private fun fit(side: Float, sum: Float) = if (sum > 0f) side / sum else 1f

private fun share(side: Float, radius: Float, other: Float) = if (radius > 0f) side * radius / (radius + other) else 0f
