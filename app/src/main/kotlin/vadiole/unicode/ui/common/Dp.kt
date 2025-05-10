package vadiole.unicode.ui.common

import android.content.Context
import kotlin.math.ceil

fun Float.dp(context: Context) = this * context.resources.displayMetrics.density

fun Int.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()