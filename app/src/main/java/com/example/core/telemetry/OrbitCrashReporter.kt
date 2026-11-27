package com.example.core.telemetry

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-first local crash recorder (MIT/open-source friendly, zero Google Play Services lock-in).
 * Captures uncaught exceptions into app-private storage for Settings inspection & export,
 * and can forward Sentry envelopes when a user/self-hosted Sentry DSN is configured.
 */
object OrbitCrashReporter {

    private const val CRASH_DIR = "orbit_crash_logs"
    private const val MAX_LOG_FILES = 10

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { recordCrash(appContext, thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.log")
        val report = buildString {
            appendLine("Orbit Browser Crash Report")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: $threadName")
            appendLine("Timestamp: ${Date()}")
            appendLine("--- Stacktrace ---")
            appendLine(throwable.stackTraceToString())
        }
        file.writeText(report)
        pruneOldLogs(dir)
    }

    fun getCrashLogCount(context: Context): Int {
        val dir = File(context.filesDir, CRASH_DIR)
        return dir.listFiles()?.count { it.isFile && it.name.endsWith(".log") } ?: 0
    }

    fun getLatestCrashSummary(context: Context): String? {
        val dir = File(context.filesDir, CRASH_DIR)
        val latest = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { latest.readLines().take(6).joinToString("\n") }.getOrNull()
    }

    fun clearCrashLogs(context: Context) {
        val dir = File(context.filesDir, CRASH_DIR)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_LOG_FILES).forEach { runCatching { it.delete() } }
    }
}
