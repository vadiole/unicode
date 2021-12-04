package vadiole.unicode.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import vadiole.unicode.ui.theme.AppTheme
import vadiole.unicode.utils.toClipboard

fun Context.dialogCharDetails(appTheme: AppTheme, char: String, description: String, codePoint: Int): AlertDialog {
    val dialog = AlertDialog(
        this,
        appTheme = appTheme,
        title = char,
        message = description,
        buttonPositiveText = "Copy",
        buttonNeutralText = "View online",
    )

    dialog.onClickPositive { _, _ ->
        this.toClipboard("Unicode", char)
        Toast.makeText(this, "$char copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    dialog.onClickNeutral { _, _ ->
        val hex = codePoint.toString(16)
        val url = Uri.parse("https://unicode-table.com/en/${hex}")
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(url)
        try {
            this.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Web browser not installed", Toast.LENGTH_SHORT).show()
        }
    }

    return dialog
}