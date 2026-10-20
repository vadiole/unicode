package vadiole.unicode.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import vadiole.unicode.R

class FloatingSurfaceDrawable(
    context: Context,
    private val pathProvider: PathProvider,
) : Drawable() {
    fun interface PathProvider {
        fun update(bounds: Rect, borderInset: Float, surfacePath: Path, borderPath: Path)
    }

    private val borderWidth = 1f.dp(context)

    private val surfacePath = Path()
    private val borderPath = Path()

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.layer1)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.floatingBorder)
    }

    override fun onBoundsChange(bounds: Rect) {
        surfacePath.rewind()
        borderPath.rewind()
        pathProvider.update(bounds, borderWidth / 2f, surfacePath, borderPath)
    }

    override fun getOutline(outline: Outline) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !surfacePath.isEmpty) {
            outline.setPath(surfacePath)
            outline.alpha = 1f
        } else {
            outline.setEmpty()
        }
    }

    fun copySurfacePath(out: Path): Boolean {
        if (surfacePath.isEmpty) {
            out.rewind()
            return false
        }
        out.set(surfacePath)
        return true
    }

    override fun draw(canvas: Canvas) {
        if (surfacePath.isEmpty) return
        canvas.drawPath(surfacePath, surfacePaint)
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private fun addSquircles(rect: RectF, bounds: Rect, borderInset: Float, radius: Float, surfacePath: Path, borderPath: Path) {
            rect.set(bounds)
            surfacePath.addSquircle(rect, radius)
            rect.inset(borderInset, borderInset)
            borderPath.addSquircle(rect, radius - borderInset)
        }

        fun circle(): PathProvider = PathProvider { bounds, borderInset, surfacePath, borderPath ->
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val radius = minOf(bounds.width(), bounds.height()) / 2f
            surfacePath.addCircle(cx, cy, radius, Path.Direction.CW)
            borderPath.addCircle(cx, cy, radius - borderInset, Path.Direction.CW)
        }

        fun pill(): PathProvider = PathProvider { bounds, borderInset, surfacePath, borderPath ->
            val left = bounds.left.toFloat()
            val top = bounds.top.toFloat()
            val right = bounds.right.toFloat()
            val bottom = bounds.bottom.toFloat()
            val radius = bounds.height() / 2f
            surfacePath.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW)
            val borderRadius = radius - borderInset
            borderPath.addRoundRect(
                left + borderInset, top + borderInset, right - borderInset, bottom - borderInset,
                borderRadius, borderRadius, Path.Direction.CW
            )
        }

        fun squircleWithTopArrow(
            cornerRadius: Int,
            arrowWidth: Float,
            arrowHeight: Float,
            arrowOverlap: Float = 0f,
            arrowTipRadius: Float = 0f,
            arrowCenterX: () -> Float = { Float.NaN },
        ): PathProvider {
            val rect = RectF()
            val arrow = Path()
            return PathProvider { bounds, borderInset, surfacePath, borderPath ->
                addSquircles(rect, bounds, borderInset, cornerRadius.toFloat(), surfacePath, borderPath)

                val halfArrow = arrowWidth / 2f
                val minX = bounds.left + cornerRadius + halfArrow
                val maxX = bounds.right - cornerRadius - halfArrow
                val anchorX = arrowCenterX()
                val cx = if (anchorX.isNaN() || minX > maxX) bounds.exactCenterX() else anchorX.coerceIn(minX, maxX)
                val baseY = bounds.top + arrowOverlap
                val tipY = bounds.top - arrowHeight + arrowOverlap
                val tipRadius = arrowTipRadius.coerceIn(0f, minOf(halfArrow, arrowHeight))

                arrow.rewind()
                arrow.moveTo(cx - halfArrow, baseY)
                if (tipRadius > 0f) {
                    arrow.lineTo(cx - tipRadius, tipY + tipRadius)
                    arrow.cubicTo(
                        cx - tipRadius * 0.5f, tipY,
                        cx + tipRadius * 0.5f, tipY,
                        cx + tipRadius, tipY + tipRadius
                    )
                } else {
                    arrow.lineTo(cx, tipY)
                }
                arrow.lineTo(cx + halfArrow, baseY)
                arrow.close()

                surfacePath.op(arrow, Path.Op.UNION)
                borderPath.op(arrow, Path.Op.UNION)
            }
        }

        fun squircleWithArrow(
            cornerRadius: Int,
            arrowWidth: Float,
            arrowHeight: Float,
            arrowOverlap: Float = 0f,
        ): PathProvider {
            val rect = RectF()
            val arrow = Path()
            return PathProvider { bounds, borderInset, surfacePath, borderPath ->
                addSquircles(rect, bounds, borderInset, cornerRadius.toFloat(), surfacePath, borderPath)

                val cy = bounds.exactCenterY()
                val halfArrow = arrowHeight / 2f
                val baseX = bounds.right - arrowOverlap
                val tipX = bounds.right + arrowWidth

                arrow.rewind()
                arrow.moveTo(baseX, cy - halfArrow)
                arrow.cubicTo(
                    baseX, cy - halfArrow * 0.5f,
                    tipX, cy - halfArrow * 0.3f,
                    tipX, cy
                )
                arrow.cubicTo(
                    tipX, cy + halfArrow * 0.3f,
                    baseX, cy + halfArrow * 0.5f,
                    baseX, cy + halfArrow
                )
                arrow.close()

                surfacePath.op(arrow, Path.Op.UNION)
                borderPath.op(arrow, Path.Op.UNION)
            }
        }
    }
}
