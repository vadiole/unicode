package vadiole.unicode.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.View
import java.util.Hashtable

private const val semiboldPath = "font/roboto_semibold.otf"
val View.roboto_semibold: Typeface?
    get() = getTypeface(context, semiboldPath)

private const val regularPath = "font/roboto_regular.otf"
val View.roboto_regular: Typeface?
    get() = getTypeface(context, regularPath)

private val typefaceCache = Hashtable<String, Typeface>()

private fun getTypeface(context: Context, assetPath: String): Typeface? = synchronized(typefaceCache) {
    if (!typefaceCache.containsKey(assetPath)) {
        try {
            val builder = Typeface.Builder(context.applicationContext.assets, assetPath)
            if (assetPath.contains("semibold")) {
                builder.setWeight(600)
            } else if (assetPath.contains("medium")) {
                builder.setWeight(400)
            }
            if (assetPath.contains("italic")) {
                builder.setItalic(true)
            }
            val typeface: Typeface = builder.build()
            typefaceCache[assetPath] = typeface
        } catch (e: Exception) {
            Log.e("TYPEFACE", "getTypeface: ", e)
            return null
        }
    }
    return typefaceCache[assetPath]
}