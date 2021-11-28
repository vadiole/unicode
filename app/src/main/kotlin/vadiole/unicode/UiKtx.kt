package vadiole.unicode

import android.content.Context
import android.view.ViewGroup
import kotlin.math.ceil

const val fill = ViewGroup.LayoutParams.MATCH_PARENT
const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

fun Float.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()

fun Float.dpf(context: Context): Float = this * context.resources.displayMetrics.density

fun Int.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()

fun Int.dpf(context: Context): Float = this * context.resources.displayMetrics.density