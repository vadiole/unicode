package vadiole.unicode.ui.table.selector

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow

open class BlockSelectorPopup(val view: BlockSelectorView, x: Int, y: Int) : PopupWindow(view, x, y) {
    init {
        isFocusable = true
        animationStyle = 0
        isOutsideTouchable = true
        isClippingEnabled = true
    }

    fun dimBehind() {
        val container = contentView.rootView
        val context = contentView.context
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = (container.layoutParams as WindowManager.LayoutParams).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.2f
        }
        windowManager.updateViewLayout(container, layoutParams)
    }

    private fun dismissDim() {
        val container = contentView.rootView
        val context = contentView.context
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (container.layoutParams == null || container.layoutParams !is WindowManager.LayoutParams) {
            return
        }
        val layoutParams = container.layoutParams as WindowManager.LayoutParams
        try {
            if (layoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                layoutParams.apply {
                    flags = flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                    dimAmount = 0.0f
                }
                windowManager.updateViewLayout(container, layoutParams)
            }
        } catch (ignore: Exception) {
        }
    }

    override fun dismiss() {
        dismissDim()
        view.dismiss { super.dismiss() }
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        dimBehind()
        view.show()
    }
}
