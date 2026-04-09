package vadiole.unicode.ui.common

import android.graphics.Path
import kotlin.math.min

/**
 * iOS-like rounded rectangle with continuous curvature (squircle).
 *
 * Optimized version: precomputed constants (no pow/sqrt at runtime),
 * zero per-call heap allocations, bounds caching, and translation offset.
 *
 * [Read more about Squircle](https://www.figma.com/blog/desperately-seeking-squircles)
 */
class Squircle4(
    topLeftRadius: Int,
    topRightRadius: Int,
    bottomRightRadius: Int,
    bottomLeftRadius: Int,
    smoothing: Float = SMOOTHING_IOS,
) {

    constructor(radius: Int) : this(radius, radius, radius, radius)
    constructor(radius: Int, smoothing: Float) : this(radius, radius, radius, radius, smoothing)

    val path = Path()

    private val r0 = topLeftRadius.coerceAtLeast(0).toFloat()
    private val r1 = topRightRadius.coerceAtLeast(0).toFloat()
    private val r2 = bottomRightRadius.coerceAtLeast(0).toFloat()
    private val r3 = bottomLeftRadius.coerceAtLeast(0).toFloat()
    private val smoothing = smoothing.coerceIn(0f, 1f)
    private val isUniform = r0 == r1 && r1 == r2 && r2 == r3

    // Workspace for clamped radii (non-uniform path only)
    private val cr = FloatArray(4)

    // Per-corner bezier control point offsets
    private val pa = FloatArray(4)
    private val pb = FloatArray(4)
    private val pc = FloatArray(4)
    // Per-corner total edge consumption: r * (1 + smoothing)
    private val pd = FloatArray(4)

    // Bounds cache for skip/offset optimization
    private var lastL = Float.NaN
    private var lastT = Float.NaN
    private var lastR = Float.NaN
    private var lastB = Float.NaN

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        recalculate(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        recalculate(left, top, right, bottom)
    }

    private fun recalculate(left: Float, top: Float, right: Float, bottom: Float) {
        if (left == lastL && top == lastT && right == lastR && bottom == lastB) return

        val w = right - left
        val h = bottom - top

        // Same size, different position — offset the existing path
        val lw = lastR - lastL
        val lh = lastB - lastT
        if (w == lw && h == lh) {
            path.offset(left - lastL, top - lastT)
            lastL = left; lastT = top; lastR = right; lastB = bottom
            return
        }

        lastL = left; lastT = top; lastR = right; lastB = bottom

        if (w <= 0f || h <= 0f) {
            path.rewind()
            return
        }

        if (isUniform) {
            computeUniform(w, h)
        } else {
            computeNonUniform(w, h)
        }

        buildPath(left, top, right, bottom)
    }

    private fun computeUniform(w: Float, h: Float) {
        val ri = min(r0, min(w, h) * 0.5f)
        if (ri <= 0f) {
            pd[0] = 0f; pd[1] = 0f; pd[2] = 0f; pd[3] = 0f
            return
        }
        val twoR = ri + ri
        val si = min(
            ((w / twoR) - 1f).coerceIn(0f, this.smoothing),
            ((h / twoR) - 1f).coerceIn(0f, this.smoothing),
        )
        val ci = C * ri
        val bi = B * ri
        val di = ri * (1f + si)
        val ai = di - ci - ci - bi
        pa[0] = ai; pa[1] = ai; pa[2] = ai; pa[3] = ai
        pb[0] = bi; pb[1] = bi; pb[2] = bi; pb[3] = bi
        pc[0] = ci; pc[1] = ci; pc[2] = ci; pc[3] = ci
        pd[0] = di; pd[1] = di; pd[2] = di; pd[3] = di
    }

    private fun computeNonUniform(w: Float, h: Float) {
        cr[0] = r0; cr[1] = r1; cr[2] = r2; cr[3] = r3
        var scale = 1f
        val ts = cr[0] + cr[1]
        if (ts > 0f) scale = min(scale, w / ts)
        val rs = cr[1] + cr[2]
        if (rs > 0f) scale = min(scale, h / rs)
        val bs = cr[2] + cr[3]
        if (bs > 0f) scale = min(scale, w / bs)
        val ls = cr[3] + cr[0]
        if (ls > 0f) scale = min(scale, h / ls)
        if (scale < 1f) {
            cr[0] *= scale; cr[1] *= scale; cr[2] *= scale; cr[3] *= scale
        }

        val s01 = cr[0] + cr[1]
        val eTop = if (s01 > 0f) ((w / s01) - 1f).coerceIn(0f, this.smoothing) else this.smoothing
        val s12 = cr[1] + cr[2]
        val eRight = if (s12 > 0f) ((h / s12) - 1f).coerceIn(0f, this.smoothing) else this.smoothing
        val s23 = cr[2] + cr[3]
        val eBottom = if (s23 > 0f) ((w / s23) - 1f).coerceIn(0f, this.smoothing) else this.smoothing
        val s30 = cr[3] + cr[0]
        val eLeft = if (s30 > 0f) ((h / s30) - 1f).coerceIn(0f, this.smoothing) else this.smoothing

        val s0 = min(eTop, eLeft)
        val s1 = min(eTop, eRight)
        val s2 = min(eRight, eBottom)
        val s3 = min(eBottom, eLeft)

        for (i in 0..3) {
            val ri = cr[i]
            if (ri <= 0f) {
                pa[i] = 0f; pb[i] = 0f; pc[i] = 0f; pd[i] = 0f
            } else {
                val si = when (i) { 0 -> s0; 1 -> s1; 2 -> s2; else -> s3 }
                val ci = C * ri
                val bi = B * ri
                val di = ri * (1f + si)
                pa[i] = di - ci - ci - bi
                pb[i] = bi
                pc[i] = ci
                pd[i] = di
            }
        }
    }

    private fun buildPath(left: Float, top: Float, right: Float, bottom: Float) {
        path.rewind()

        val a0 = pa[0]; val b0 = pb[0]; val c0 = pc[0]; val d0 = pd[0]
        val a1 = pa[1]; val b1 = pb[1]; val c1 = pc[1]; val d1 = pd[1]
        val a2 = pa[2]; val b2 = pb[2]; val c2 = pc[2]; val d2 = pd[2]
        val a3 = pa[3]; val b3 = pb[3]; val c3 = pc[3]; val d3 = pd[3]

        // Top-left
        if (d0 > 0f) {
            val ab = a0 + b0
            path.moveTo(left, top + d0)
            path.rCubicTo(0f, -a0, 0f, -ab, c0, -(ab + c0))
            path.rCubicTo(c0, -c0, b0 + c0, -c0, ab + c0, -c0)
        } else {
            path.moveTo(left, top)
        }

        // Top-right
        if (d1 > 0f) {
            val ab = a1 + b1
            path.lineTo(right - d1, top)
            path.rCubicTo(a1, 0f, ab, 0f, ab + c1, c1)
            path.rCubicTo(c1, c1, c1, b1 + c1, c1, ab + c1)
        } else {
            path.lineTo(right, top)
        }

        // Bottom-right
        if (d2 > 0f) {
            val ab = a2 + b2
            path.lineTo(right, bottom - d2)
            path.rCubicTo(0f, a2, 0f, ab, -c2, ab + c2)
            path.rCubicTo(-c2, c2, -(b2 + c2), c2, -(ab + c2), c2)
        } else {
            path.lineTo(right, bottom)
        }

        // Bottom-left
        if (d3 > 0f) {
            val ab = a3 + b3
            path.lineTo(left + d3, bottom)
            path.rCubicTo(-a3, 0f, -ab, 0f, -(ab + c3), -c3)
            path.rCubicTo(-c3, -c3, -c3, -(b3 + c3), -c3, -(ab + c3))
        } else {
            path.lineTo(left, bottom)
        }

        path.close()
    }

    companion object {
        const val SMOOTHING_IOS = 0.6f
        private const val C = 0.2929f    // 1 - cos(45°)
        private const val B = 0.36398f   // 3√2 × C² — replaces pow(1.5) computation
    }
}
