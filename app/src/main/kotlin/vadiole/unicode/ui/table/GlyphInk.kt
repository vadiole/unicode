package vadiole.unicode.ui.table

import android.graphics.Paint
import android.graphics.Rect
import android.util.SparseIntArray
import vadiole.unicode.data.CodePoint

object GlyphInk {
    private const val SIZE = 100f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = SIZE }
    private val bounds = Rect()
    private val slots = SparseIntArray()
    private var boxes = FloatArray(4 * 256)

    fun load(codePoint: CodePoint, out: FloatArray, offset: Int) {
        var slot = slots.get(codePoint.value, -1)
        if (slot < 0) {
            slot = slots.size()
            if ((slot + 1) * 4 > boxes.size) boxes = boxes.copyOf(boxes.size * 2)
            measure(codePoint, slot * 4)
            slots.put(codePoint.value, slot)
        }
        System.arraycopy(boxes, slot * 4, out, offset, 4)
    }

    private fun measure(codePoint: CodePoint, offset: Int) {
        val char = codePoint.char
        paint.getTextBounds(char, 0, char.length, bounds)
        if (bounds.isEmpty) {
            boxes.fill(0f, offset, offset + 4)
            return
        }
        val originX = paint.measureText(char) / 2f
        boxes[offset] = (bounds.left - originX) / SIZE
        boxes[offset + 1] = bounds.top / SIZE
        boxes[offset + 2] = (bounds.right - originX) / SIZE
        boxes[offset + 3] = bounds.bottom / SIZE
    }
}
