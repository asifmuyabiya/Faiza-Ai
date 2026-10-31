package com.faiza.ai.assist

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.faiza.ai.FaizaApp
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.DashboardActivity

/**
 * Default Assistant (video 7:56): hold the power / home button (or a headset button) and Faiza
 * starts listening, like Google Assistant.
 */
object AssistLauncher {

    fun isDefaultAssistant(ctx: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            Settings.Secure.getString(ctx.contentResolver, "assistant")?.startsWith(ctx.packageName) == true
        }
    }.getOrDefault(false)

    /** Android only lets the user pick the assistant in Settings. */
    fun openAssistantSettings(ctx: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in tries) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** The assistant button was pressed: Faiza starts listening. */
    fun activate(ctx: Context) {
        val service = FloatingOrbService.instance
        if (service != null) {
            service.activateFromAssist()
            return
        }
        // Not running yet: a (see-through) activity may start the service even from the lock screen.
        runCatching {
            ctx.startActivity(
                Intent(ctx, AssistActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}

/** Registered with Android as Faiza's assistant service; the work happens in [FaizaAssistSession]. */
class FaizaVoiceInteractionService : VoiceInteractionService()

class FaizaAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = FaizaAssistSession(this)
}

class FaizaAssistSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AssistLauncher.activate(context)
        hide()
    }
}

/** Also opened by older launchers (ACTION_ASSIST) and headset buttons (VOICE_COMMAND). No screen of its own. */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = FloatingOrbService.instance
        val prefs = FaizaApp.instance.prefs
        when {
            service != null -> service.activateFromAssist()
            !Settings.canDrawOverlays(this) || !prefs.isAiConfigured() ->
                // Setup isn't finished: show the app so the user can finish it.
                startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> {
                FloatingOrbService.pendingAssist = true
                runCatching { startForegroundService(Intent(this, FloatingOrbService::class.java)) }
                    .onFailure { FloatingOrbService.pendingAssist = false }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/**
 * Picks a real speech recognizer (Google's) that is not Faiza's own: when Faiza becomes the default
 * assistant, Android also makes her the phone's speech recognizer, so she must pass the work on.
 */
object RecognizerPicker {

    fun otherComponent(ctx: Context): ComponentName? {
        val found = runCatching {
            ctx.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
                .filter { it.packageName != ctx.packageName }
        }.getOrDefault(emptyList())
        return found.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
            ?: found.firstOrNull { it.packageName.startsWith("com.google.") }
            ?: found.firstOrNull()
    }

    fun createOther(ctx: Context): SpeechRecognizer? =
        otherComponent(ctx)?.let { runCatching { SpeechRecognizer.createSpeechRecognizer(ctx, it) }.getOrNull() }

    /** For Faiza's own listening: never loop back into herself. */
    fun create(ctx: Context): SpeechRecognizer = createOther(ctx) ?: SpeechRecognizer.createSpeechRecognizer(ctx)
}

/** Faiza's speech recognizer: passes every request to Google's, so voice input in other apps keeps working. */
class FaizaRecognitionService : RecognitionService() {

    private var inner: SpeechRecognizer? = null

    private inline fun safe(block: () -> Unit) {
        runCatching(block)
    }

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        inner?.destroy()
        val r = RecognizerPicker.createOther(this)
        if (r == null) {
            safe { listener.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        inner = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = safe { listener.readyForSpeech(params ?: Bundle()) }
            override fun onBeginningOfSpeech() = safe { listener.beginningOfSpeech() }
            override fun onRmsChanged(rmsdB: Float) = safe { listener.rmsChanged(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) = safe { buffer?.let { listener.bufferReceived(it) } }
            override fun onEndOfSpeech() = safe { listener.endOfSpeech() }
            override fun onError(error: Int) = safe { listener.error(error) }
            override fun onResults(results: Bundle?) = safe { listener.results(results ?: Bundle()) }
            override fun onPartialResults(partialResults: Bundle?) = safe { listener.partialResults(partialResults ?: Bundle()) }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback) {
        inner?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        inner?.cancel()
    }

    override fun onDestroy() {
        inner?.destroy()
        inner = null
        super.onDestroy()
    }
}
