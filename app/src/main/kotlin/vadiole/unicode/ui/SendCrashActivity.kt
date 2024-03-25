package vadiole.unicode.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import vadiole.unicode.BuildConfig
import vadiole.unicode.data.config.UserConfig
import vadiole.unicode.utils.extension.dp
import java.io.File
import kotlin.system.exitProcess

class SendCrashActivity : Activity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) finish()
        Process.killProcess(intent.getIntExtra(MAIN_PROCESS_PID, -1))
        val stacktracePath = intent.getStringExtra(STACKTRACE_FILE_PATH) ?: return
        val userConfig = UserConfig(this)
        dialog = AlertDialog.Builder(this)
            .setTitle("Unicode has stopped")
            .setView(
                LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    addView(
                        TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            setPadding(24.dp(context), 8.dp(context), 24.dp(context), 8.dp(context))
                            text = "Would you like to send a crash report?"
                            textSize = 17f
                        }
                    )
                    if (userConfig.firstCrashReport) {
                        userConfig.firstCrashReport = false
                    } else {
                        addView(
                            CheckBox(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(20.dp(context), 0, 20.dp(context), 0)
                                }
                                setPadding(0, 16.dp(context), 0, 16.dp(context))
                                text = "Don't ask again"
                                isChecked = false
                                setOnCheckedChangeListener { _, isChecked ->
                                    userConfig.crashReportDisabled = isChecked
                                }
                            }
                        )
                    }
                }
            )
            .setPositiveButton("Send") { _, _ ->
                val stacktraceFile = File(stacktracePath)
                if (!stacktraceFile.exists()) return@setPositiveButton
                val stacktraceUri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", stacktraceFile)
                val emailSelectorIntent = Intent(Intent.ACTION_SENDTO)
                    .setData(Uri.parse("mailto:"))
                val intent = Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(String(target)))
                    .putExtra(Intent.EXTRA_STREAM, stacktraceUri)
                    .putExtra(Intent.EXTRA_SUBJECT, "Unicode app")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .apply {
                        setSelector(emailSelectorIntent)
                        clipData = ClipData("stacktrace", arrayOf("text/plain"), ClipData.Item(stacktraceUri))
                    }
                try {
                    startActivity(Intent.createChooser(intent, "Send via email"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "No email clients installed", Toast.LENGTH_LONG).show()
                    finishAndRemoveTask()
                    exitProcess(0)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finishAndRemoveTask()
                exitProcess(0)
            }
            .setOnCancelListener { finishAndRemoveTask() }
            .create().also {
                it.show()
            }
    }

    override fun onStop() {
        super.onStop()
        finishAndRemoveTask()
        exitProcess(0)
    }

    override fun onResume() {
        super.onResume()
        if (dialog?.isShowing == false) {
            finishAndRemoveTask()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        dialog = null
    }

    companion object {
        const val STACKTRACE_FILE_PATH = "stacktrace_file_path"
        const val MAIN_PROCESS_PID = "main_process_pid"
        private val target = byteArrayOf(
            0x75, 0x6e, 0x69, 0x63, 0x6f, 0x64, 0x65, 0x40, 0x76,
            0x61, 0x64, 0x69, 0x6f, 0x6c, 0x65, 0x2e, 0x6d, 0x65,
        )
    }
}