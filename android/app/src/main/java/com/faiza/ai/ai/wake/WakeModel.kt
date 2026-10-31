package com.faiza.ai.ai.wake

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Offline speech model for "Hey Maya" (Vosk, Indian English, ~36 MB). */
object WakeModel : DownloadableModel("vosk-model-small-en-in-0.4", sizeLabel = "36 MB")

/** Offline voice-print model for Voice Guardian (Vosk speaker ID, ~13 MB, any language). */
object SpeakerModelFiles : DownloadableModel("vosk-model-spk-0.4", sizeLabel = "13 MB")

/** A Vosk model downloaded once from alphacephei.com into the app's private storage. */
open class DownloadableModel(val name: String, val sizeLabel: String) {

    sealed class State {
        object Missing : State()
        data class Downloading(val percent: Int) : State()
        object Ready : State()
        data class Failed(val reason: String) : State()
    }

    private val url = "https://alphacephei.com/vosk/models/$name.zip"
    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.faiza.ai.util.CrashReporter.guard)
    @Volatile private var job: Job? = null

    fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, name)
    private fun marker(ctx: Context) = File(dir(ctx), ".ready")

    fun isReady(ctx: Context): Boolean = marker(ctx).exists()

    /** Updates [state] from disk (call when a screen opens). */
    fun refresh(ctx: Context) {
        if (isReady(ctx)) _state.value = State.Ready
        else if (job?.isActive != true && _state.value is State.Ready) _state.value = State.Missing
    }

    /** Starts the download if the model isn't on the phone yet. Safe to call repeatedly. */
    fun ensureDownloaded(ctx: Context) {
        val app = ctx.applicationContext
        if (isReady(app)) {
            _state.value = State.Ready
            return
        }
        if (job?.isActive == true) return
        job = scope.launch { download(app) }
    }

    /** Deletes a broken model so it is downloaded again next time. */
    fun reset(ctx: Context) {
        VoskRuntime.forget(name)
        dir(ctx).deleteRecursively()
        _state.value = State.Missing
    }

    private fun download(ctx: Context) {
        val tmp = File(ctx.cacheDir, "$name.zip")
        try {
            _state.value = State.Downloading(0)
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("server returned ${response.code}")
                val body = response.body ?: throw IOException("empty download")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = State.Downloading(percent)
                                }
                            }
                        }
                    }
                }
            }

            val target = dir(ctx)
            target.deleteRecursively()
            unzip(tmp, ctx.filesDir)
            if (!looksComplete(target)) {
                // Some archives use a different top folder name: find it and rename.
                ctx.filesDir.listFiles()
                    ?.firstOrNull { it.isDirectory && it.name.startsWith(name.substringBeforeLast('-')) && it != target && looksComplete(it) }
                    ?.renameTo(target)
            }
            if (!looksComplete(target)) throw IOException("unexpected model files")
            marker(ctx).writeText("ok")
            _state.value = State.Ready
        } catch (e: Exception) {
            dir(ctx).deleteRecursively()
            _state.value = State.Failed(e.message ?: "download failed")
        } finally {
            tmp.delete()
        }
    }

    private fun looksComplete(d: File): Boolean =
        d.isDirectory && (d.walkTopDown().count { it.isFile } >= 3)

    private fun unzip(zip: File, dest: File) {
        val root = dest.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(root)) throw IOException("unsafe file in archive")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
