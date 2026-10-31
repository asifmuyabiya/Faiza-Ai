package com.faiza.ai.ai.code

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs shell commands inside Termux (owner: "code লিখবে Termux-এর ভেতরে"). Needs, once:
 *  • Termux from F-Droid/GitHub, • "allow-external-apps=true" in ~/.termux/termux.properties,
 *  • Faiza's "Run commands in Termux" permission.
 */
object TermuxBridge {

    const val PKG = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private val ids = AtomicInteger(4100)

    /** What the owner pastes into Termux once. */
    const val SETUP_COMMAND =
        "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings; " +
                "termux-setup-storage; pkg install -y python nodejs"

    data class Result(val exit: Int, val stdout: String, val stderr: String, val error: String? = null) {
        val ok get() = error == null && exit == 0
        fun report(max: Int = 6000): String = buildString {
            if (error != null) append("ERROR: $error\n")
            append("exit code: $exit\n")
            if (stdout.isNotBlank()) append("--- stdout ---\n").append(stdout.takeLast(max)).append('\n')
            if (stderr.isNotBlank()) append("--- stderr ---\n").append(stderr.takeLast(max)).append('\n')
        }
    }

    fun installed(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun problem(ctx: Context): String? = when {
        !installed(ctx) -> "Termux isn't installed (get it from F-Droid or GitHub, not the Play Store)."
        !hasPermission(ctx) -> "Faiza needs the \"Run commands in Termux\" permission — open Code Studio and tap Termux setup."
        !CodeProjects.sharedStorage() -> "Faiza needs \"All files access\" so Termux can see the project folder."
        else -> null
    }

    /** Runs [command] with bash in [workdir]; gives up after [timeoutSec] (the command is also wrapped in `timeout`). */
    suspend fun run(ctx: Context, command: String, workdir: String, timeoutSec: Int = 120): Result {
        problem(ctx)?.let { return Result(-1, "", "", it) }
        val app = ctx.applicationContext
        val id = ids.incrementAndGet()
        val action = "${app.packageName}.TERMUX_RESULT_$id"
        val done = CompletableDeferred<Result>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val b = i.getBundleExtra("result")
                done.complete(
                    Result(
                        b?.getInt("exitCode", -1) ?: -1,
                        b?.getString("stdout").orEmpty(),
                        b?.getString("stderr").orEmpty(),
                        b?.getString("errmsg")?.takeIf { (b.getInt("err", -1)) > 0 && it.isNotBlank() }
                    )
                )
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val pi = PendingIntent.getBroadcast(
                app, id, Intent(action).setPackage(app.packageName),
                PendingIntent.FLAG_ONE_SHOT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            )
            val wrapped = "export HOME=/data/data/com.termux/files/home; cd ${quote(workdir)} && timeout ${timeoutSec}s bash -c ${quote(command)} 2>&1 | tail -c 60000; exit \${PIPESTATUS[0]}"
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(PKG, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", wrapped))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
            }
            runCatching { app.startService(intent) }.onFailure {
                return Result(-1, "", "", "Couldn't reach Termux: ${it.message} (is allow-external-apps=true set?)")
            }
            return withTimeoutOrNull((timeoutSec + 30) * 1000L) { done.await() }
                ?: Result(-1, "", "", "No answer from Termux after ${timeoutSec + 30}s (open Termux once, check allow-external-apps).")
        } finally {
            runCatching { app.unregisterReceiver(receiver) }
        }
    }

    /** Starts a long-running command (a server) and returns at once. */
    suspend fun startBackground(ctx: Context, command: String, workdir: String): Result =
        run(ctx, "nohup bash -c ${quote(command)} > .faiza/server.log 2>&1 & sleep 3; tail -n 30 .faiza/server.log", workdir, 20)

    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
