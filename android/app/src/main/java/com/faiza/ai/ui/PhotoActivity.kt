package com.faiza.ai.ui

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/** One photo taken for the user's own request: preview, 3-2-1, click, saved to Pictures/Faiza AI. */
class PhotoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRONT = "front"
        const val EXTRA_COUNTDOWN = "countdown"

        /** The take_photo tool waits on this: the saved photo, or null if it failed / was closed. */
        @Volatile var pending: CompletableDeferred<Uri?>? = null
    }

    private lateinit var previewView: PreviewView
    private lateinit var counter: TextView
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        root.addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        counter = TextView(this).apply {
            textSize = 96f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(18f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        root.addView(counter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        startCamera(intent.getBooleanExtra(EXTRA_FRONT, true), intent.getIntExtra(EXTRA_COUNTDOWN, 3).coerceIn(0, 10))
    }

    private fun startCamera(front: Boolean, countdown: Int) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
                tick(countdown, capture)
            }.onFailure { finishWith(null) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tick(n: Int, capture: ImageCapture) {
        if (finished) return
        if (n <= 0) { counter.text = ""; shoot(capture); return }
        counter.text = n.toString()
        counter.postDelayed({ tick(n - 1, capture) }, 1_000)
    }

    private fun shoot(capture: ImageCapture) {
        val name = "Faiza_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Faiza AI")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = output.savedUri
                // Show the photo for a moment, then close.
                val shown = ImageView(this@PhotoActivity).apply {
                    setImageURI(uri)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.BLACK)
                }
                setContentView(shown)
                shown.postDelayed({ finishWith(uri) }, 1_800)
            }

            override fun onError(exception: ImageCaptureException) = finishWith(null)
        })
    }

    private fun finishWith(uri: Uri?) {
        if (finished) return
        finished = true
        pending?.complete(uri)
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        finish()
    }

    override fun onDestroy() {
        if (!finished) pending?.complete(null)
        super.onDestroy()
    }
}
