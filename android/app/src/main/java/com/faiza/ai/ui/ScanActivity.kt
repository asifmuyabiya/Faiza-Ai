package com.faiza.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ChatTurn
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.util.AttachmentReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scan mode (video 30:51): Faiza looks through the camera. With Live voice she sees about one
 * picture per second and you simply ask out loud; otherwise type a question and she answers in text.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        private const val FRAME_EVERY_MS = 1_000L
        private const val SCAN_OPENED =
            "[SCAN MODE: the user opened the camera. From now on you also receive what their camera sees, about one " +
                    "picture per second. Say in one short sentence that you can see now and ask what they'd like to know. " +
                    "When they ask about something, answer from the latest picture.]"
    }

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var previewView: PreviewView
    private lateinit var analysisExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    /** Latest frame (JPEG), kept for typed questions. */
    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile private var lastFrameAt = 0L
    @Volatile private var visible = false
    private var liveMode = false
    private var introSent = false
    private var statusJob: Job? = null
    private var asking: Job? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionBox()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        analysisExecutor = Executors.newSingleThreadExecutor()
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        findViewById<FrameLayout>(R.id.previewContainer).addView(previewView)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnFlip).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        findViewById<View>(R.id.btnAllowCamera).setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                cameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                // Permanently denied: only the app's settings page can turn it back on.
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            }
        }
        findViewById<ImageButton>(R.id.btnAsk).setOnClickListener { ask() }
        findViewById<EditText>(R.id.etQuestion).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { ask(); true } else false
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        connectFaiza()
    }

    override fun onPause() {
        visible = false
        statusJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    // ===== Faiza =====

    /** Live voice available: make sure the conversation is running so she can see and hear. */
    private fun connectFaiza() {
        liveMode = prefs.canUseLive()
        if (liveMode) {
            val service = FloatingOrbService.instance
            if (service == null) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    FloatingOrbService.pendingTypedText = SCAN_OPENED
                    introSent = true
                    runCatching { startForegroundService(Intent(this, FloatingOrbService::class.java)) }
                } else liveMode = false
            } else {
                service.ensureLive()
                if (!introSent) {
                    introSent = true
                    service.sendInstruction(SCAN_OPENED)
                }
            }
        }
        statusJob?.cancel()
        statusJob = lifecycleScope.launch {
            while (isActive) {
                updateStatus()
                delay(1_000)
            }
        }
    }

    private fun updateStatus() {
        val status = findViewById<TextView>(R.id.tvScanStatus)
        val dot = findViewById<View>(R.id.statusDot)
        val name = prefs.assistantName(Persona.from(prefs.selectedPersona))
        if (liveMode && FloatingOrbService.instance?.canSeeCamera == false) liveMode = false
        when {
            !liveMode -> {
                status.text = "Type a question below"
                dot.visibility = View.GONE
            }
            FloatingOrbService.instance?.isLiveReady == true -> {
                status.text = "$name is looking · ask out loud"
                dot.visibility = View.VISIBLE
            }
            else -> {
                status.text = "Connecting…"
                dot.visibility = View.GONE
            }
        }
    }

    private fun ask() {
        val box = findViewById<EditText>(R.id.etQuestion)
        val question = box.text.toString().trim()
        if (question.isEmpty()) return
        val frame = latestJpeg
        if (frame == null) {
            Toast.makeText(this, "The camera isn't ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        box.setText("")
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(box.windowToken, 0)

        val service = FloatingOrbService.instance
        if (liveMode && service?.isLiveReady == true) {
            service.sendCameraFrame(frame)
            service.handleTyped(question)
            return
        }
        // No live voice: one picture + the question, answered in text.
        val provider = AIProviders.fromPrefs(prefs)
        if (provider == null) {
            showAnswer("I need an AI key first. Open Settings → AI engine → API key.")
            return
        }
        if (asking?.isActive == true) return
        showAnswer("Looking…")
        asking = lifecycleScope.launch {
            val answer = runCatching {
                val system = "You see a photo from the user's phone camera. Answer their question about it precisely " +
                        "and briefly (1–3 sentences), in the language of the question. Plain text, no markdown."
                provider.chat(system, listOf(ChatTurn.User(question, Base64.encodeToString(frame, Base64.NO_WRAP))), emptyList())
                    .text?.trim().orEmpty().ifEmpty { "I couldn't tell from this picture." }
            }.getOrElse { "Couldn't reach the AI. Check your internet and try again." }
            showAnswer(answer)
        }
    }

    private fun showAnswer(text: String) {
        findViewById<TextView>(R.id.tvAnswer).apply {
            this.text = text
            visibility = View.VISIBLE
        }
    }

    // ===== Camera =====

    private fun showPermissionBox() {
        findViewById<View>(R.id.permissionBox).visibility = View.VISIBLE
    }

    private fun startCamera() {
        findViewById<View>(R.id.permissionBox).visibility = View.GONE
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisExecutor) { image -> onFrame(image) } }
            var selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!runCatching { provider.hasCamera(selector) }.getOrDefault(false)) {
                lensFacing = CameraSelector.LENS_FACING_BACK
                selector = CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open the camera", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** About once a second: turn the camera image into a small JPEG and (in live mode) show it to Faiza. */
    private fun onFrame(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_EVERY_MS) return
            lastFrameAt = now
            val rotation = image.imageInfo.rotationDegrees
            var bmp: Bitmap = image.toBitmap()
            if (rotation != 0) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }
            val jpeg = AttachmentReader.jpegBytes(bmp, 768, 70)
            latestJpeg = jpeg
            if (visible && liveMode) FloatingOrbService.instance?.takeIf { it.isLiveReady }?.sendCameraFrame(jpeg)
        } catch (_: Exception) {
            // Skip a bad frame; the next one comes in a second.
        } finally {
            image.close()
        }
    }
}
