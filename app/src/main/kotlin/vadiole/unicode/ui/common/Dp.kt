package vadiole.unicode.ui.common

import android.content.Context
import android.util.TypedValue
import kotlin.math.ceil

fun Float.dp(context: Context) = this * context.resources.displayMetrics.density

fun Int.dp(context: Context): Int = ceil(this * context.resources.displayMetrics.density).toInt()

fun borderWidth(context: Context) = if (context.resources.configuration.isDarkMode) 1f.dp(context) else 1f

fun Float.sp(context: Context) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, context.resources.displayMetrics)
