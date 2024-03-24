package vadiole.unicode

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.system.exitProcess

class UncaughtExceptionHandler(private val context: Context) : UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        e.printStackTrace()
        val mainLooper = Looper.getMainLooper()
        val isUiThread = mainLooper.thread == Thread.currentThread()
        if (isUiThread) {
            startSendCrashActivity()
            exitProcess(1)
        } else {
            Handler(mainLooper).post {
                startSendCrashActivity()
                exitProcess(1)
            }
        }
    }

    private fun startSendCrashActivity() {
        val intent = Intent()
            .setAction("vadiole.unicode.SEND_CRASH")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        exitProcess(1)
    }
}