package com.faiza.ai.ai.vision

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ChatTurn
import com.faiza.ai.ai.provider.GeminiProvider
import com.faiza.ai.ai.web.ResearchAgent
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.ui.PhotoActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/** Get a picture (camera / screen / newest photo in the gallery) and ask the best vision model about it. */
object PhotoAsk {

    private val prefs get() = FaizaApp.instance.prefs

    sealed class Shot { data class Ok(val jpegB64: String) : Shot(); data class Fail(val why: String) : Shot() }

    /** [source] = camera | screen | gallery. */
    suspend fun get(ctx: Context, source: String?): Shot = when (source?.lowercase()) {
        "screen" -> FaizaAccessibilityService.instance?.screenshotJpeg(maxSide = 1600, quality = 85)?.let { Shot.Ok(it) }
            ?: Shot.Fail("couldn't capture the screen (needs Accessibility and Android 11+)")
        "gallery", "photo", "last" -> latestImage(ctx)?.let { uri -> load(ctx, uri)?.let { Shot.Ok(it) } }
            ?: Shot.Fail("no recent photo found in the gallery (or no permission to read photos)")
        else -> camera(ctx)
    }

    private suspend fun camera(ctx: Context): Shot {
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return Shot.Fail("camera permission is off — ask the user to allow Camera for Faiza")
        val wait = CompletableDeferred<Uri?>()
        PhotoActivity.pending = wait
        ctx.startActivity(
            Intent(ctx, PhotoActivity::class.java)
                .putExtra(PhotoActivity.EXTRA_FRONT, false)
                .putExtra(PhotoActivity.EXTRA_COUNTDOWN, 3)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val uri = withTimeoutOrNull(45_000) { wait.await() } ?: return Shot.Fail("the photo wasn't taken")
        return load(ctx, uri)?.let { Shot.Ok(it) } ?: Shot.Fail("couldn't read the photo")
    }

    private fun latestImage(ctx: Context): Uri? = runCatching {
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null }
    }.getOrNull()

    /** Big enough to read small print, small enough to send quickly. */
    suspend fun load(ctx: Context, uri: Uri, maxSide: Int = 1600): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            val s = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val small = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
            Base64.encodeToString(ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** The strongest Gemini model on the key (Pro if available) — homework and prescriptions need accuracy. */
    suspend fun ask(system: String, question: String, jpegB64: String): String {
        val key = prefs.geminiApiKey?.trim().orEmpty()
        if (key.isEmpty()) return "ERROR: needs a Gemini API key"
        val model = runCatching { ResearchAgent.model() }.getOrDefault(com.faiza.ai.ai.provider.ProviderType.GEMINI.defaultModel)
        val reply = GeminiProvider(key, model).chat(system, listOf(ChatTurn.User(question, imageJpegBase64 = jpegB64)), emptyList())
        return reply.text?.trim().orEmpty().ifEmpty { "ERROR: no answer" }
    }
}
