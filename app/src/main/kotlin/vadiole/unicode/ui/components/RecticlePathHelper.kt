package vadiole.unicode.ui.components

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.pow

object RecticlePathHelper {
    //  initialize recticle(like squircle, but can also be rectangular)
    //  more info: https://www.figma.com/blog/desperately-seeking-squircles
    fun buildPath(recticlePath: Path, viewBounds: RectF, cornerRadius: Int, skipBottom: Boolean = false) {
        val left = viewBounds.left
        val top = viewBounds.top
        val right = viewBounds.right
        val bottom = viewBounds.bottom

        val r = cornerRadius   //  corners radius in px
        val smooth = 0.6f           //  from 0 to 1, ios = 0.6

        //  a, b, c -> https://telegra.ph/file/a65d7a87521e9c75e7579.png
        val c = 0.2929f * r
        val b = (1.5f * (2f * c * c).pow(x = 1.5f) / (c * r))
        val a = r * (1 + smooth) - c - c - b

        val ab = a + b
        val cb = c + b
        val abc = a + b + c
        val abcc = a + b + c + c

        with(recticlePath) {
            rewind()
            moveTo(left, top + abcc)

            //  left top corner
            rCubicTo(0f, -a, 0f, -ab, c, -abc)
            rCubicTo(c, -c, cb, -c, abc, -c)
            lineTo(right - abcc, top)

            //  right top corner
            rCubicTo(a, 0f, ab, 0f, abc, c)
            rCubicTo(c, c, c, cb, c, abc)
            if (skipBottom) {
                lineTo(right, bottom)
            } else {
                lineTo(right, bottom - abcc)
            }

            //  right bottom corner
            if (skipBottom) {
                lineTo(left, bottom)
            } else {
                rCubicTo(0f, a, 0f, ab, -c, abc)
                rCubicTo(-c, c, -cb, c, -abc, c)
                lineTo(left + abcc, bottom)
            }

            //  left bottom corner
            if (skipBottom) {
                lineTo(-c, -abc)
            } else {
                rCubicTo(-a, 0f, -ab, 0f, -abc, -c)
                rCubicTo(-c, -c, -c, -cb, -c, -abc)
            }
            close()
            fillType = Path.FillType.EVEN_ODD
        }
    }
}