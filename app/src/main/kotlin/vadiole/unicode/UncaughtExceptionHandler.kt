package vadiole.unicode

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import vadiole.unicode.ui.SendCrashActivity
import java.io.File
import java.lang.Thread.UncaughtExceptionHandler

class UncaughtExceptionHandler(private val context: Context) : UncaughtExceptionHandler {

    private val stackTraceDir = File(context.cacheDir, "stacktrace").also { it.mkdirs() }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val mainLooper = Looper.getMainLooper()
        val isUiThread = mainLooper.thread == Thread.currentThread()
        if (isUiThread) {
            handleException(e)
        } else {
            Handler(mainLooper).post {
                handleException(e)
            }
        }
    }

    private fun handleException(e: Throwable) {
        val outputText = e.stackTraceToString() + "\n\n\n" + getDebugInfo()
        val stackTraceFile = File(stackTraceDir, "stacktrace.log").apply {
            createNewFile()
            writeText(outputText)
        }
        val intent = Intent()
            .setAction("vadiole.unicode.SEND_CRASH")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(SendCrashActivity.STACKTRACE_FILE_PATH, stackTraceFile.canonicalPath)
            .putExtra(SendCrashActivity.MAIN_PROCESS_PID, android.os.Process.myPid())
        context.startActivity(intent)
    }

    private fun getDebugInfo(): String {
        return "+------------------------------------------------------------+\n" +
                "| Timestamp: ${System.currentTimeMillis()}\n" +
                "| Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "| Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.PRODUCT})\n" +
                "| Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.VERSION.SDK_INT})\n" +
                "| Locales: ${context.resources.configuration.locales.toLanguageTags()}\n" +
                "+------------------------------------------------------------+"
    }
}