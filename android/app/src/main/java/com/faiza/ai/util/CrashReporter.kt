package com.faiza.ai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineExceptionHandler
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash safety net (owner: "ক্র্যাশ কমাও, আর ক্রেতার ফোনে কী হলো আমি যেন জানি"):
 *  • a background job that fails is logged instead of closing Faiza ([guard]);
 *  • if she does crash, the details are saved, she restarts herself a few seconds later, and the report is sent to
 *    the owner's server on the next start (shown in the admin page).
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val QUEUE_DIR = "crash_queue"
    private lateinit var app: Context

    fun install(ctx: Context) {
        app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = format(error, "crash", thread.name)
                File(app.filesDir, FILE_NAME).writeText(report)
                enqueue(report)
                // Faiza was running: bring her back in a few seconds.
                if (com.faiza.ai.service.FloatingOrbService.instance != null) scheduleRestart()
            }
            previous?.uncaughtException(thread, error)
        }
        // Send what's waiting from last time.
        Thread({ runCatching { uploadPending() } }, "crash-upload").apply { isDaemon = true }.start()
    }

    /** Add to every background scope: an error there is logged, not a crash of the whole app. */
    val guard = CoroutineExceptionHandler { _, e ->
        if (e is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
        runCatching { enqueue(format(e, "background", Thread.currentThread().name)) }
    }

    /** Report something that went wrong but was handled. */
    fun nonFatal(e: Throwable, where: String) { runCatching { enqueue(format(e, "handled: $where", Thread.currentThread().name)) } }

    fun format(error: Throwable, kind: String, thread: String): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return "Kind: $kind\n" +
                "Time: ${SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.ENGLISH).format(Date())}\n" +
                "App: ${version()} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Thread: $thread\n\n" + sw.toString().take(12_000)
    }

    private fun version(): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun queue() = File(app.filesDir, QUEUE_DIR).apply { mkdirs() }

    private fun enqueue(report: String) {
        val dir = queue()
        File(dir, "${System.currentTimeMillis()}.txt").writeText(report)
        // Keep at most 20 waiting.
        dir.listFiles()?.sortedBy { it.name }?.dropLast(20)?.forEach { it.delete() }
    }

    private fun scheduleRestart() {
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, com.faiza.ai.service.FloatingOrbService::class.java)
        val pi = if (Build.VERSION.SDK_INT >= 26)
            PendingIntent.getForegroundService(app, 7301, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        else PendingIntent.getService(app, 7301, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val at = SystemClock.elapsedRealtime() + 3_000
        runCatching {
            if (com.faiza.ai.ai.routines.RoutineCenter.canUseExactAlarms(app)) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }

    /** Sends waiting reports to the owner's server; each one is deleted once the server has it. */
    fun uploadPending() {
        if (com.faiza.ai.FaizaApp.serverMissing) return
        val files = queue().listFiles()?.sortedBy { it.name } ?: return
        val client = com.faiza.ai.ai.provider.AIProviders.http
        val deviceId = runCatching { com.faiza.ai.FaizaApp.instance.prefs.getDeviceId() }.getOrDefault("?")
        for (f in files) {
            val body = JsonObject().apply {
                addProperty("device", deviceId.hashCode().toString(16)) // not the real ID
                addProperty("version", version())
                addProperty("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                addProperty("android", Build.VERSION.SDK_INT)
                addProperty("report", f.readText().take(15_000))
            }.toString().toRequestBody("application/json".toMediaType())
            val ok = runCatching {
                client.newCall(Request.Builder().url("${com.faiza.ai.FaizaApp.SERVER_URL}/api/crash").post(body).build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) f.delete() else return // offline: try next start
        }
    }

    /** The saved crash report, removed once read; null if the app didn't crash last time. */
    fun takeLastCrash(ctx: Context): String? {
        val f = File(ctx.applicationContext.filesDir, FILE_NAME)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        f.delete()
        return text?.takeIf { it.isNotBlank() }
    }
}
