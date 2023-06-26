import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.widget.EditText
import android.widget.TextView

fun EditText.setCursorDrawable(drawable: Drawable) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        textCursorDrawable = drawable
    } else {
        try {
            @SuppressLint("BlockedPrivateApi")
            val field = TextView::class.java.getDeclaredField("mCursorDrawable")
            field.isAccessible = true
            field.set(this, drawable)
        } catch (throwable: Throwable) {
            Log.e("EditText", "Reflection", throwable)
        }
    }
}