package com.faiza.ai.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.faiza.ai.ai.guardian.OwnerGuard
import java.io.File

/**
 * Invisible screen that takes one front-camera photo of whoever is holding the phone (Owner lock), saves it to
 * "Intruder alerts" and closes. A visible screen is needed because Android doesn't let background apps use the camera.
 */
class IntruderCaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STRIKE = "strike"
    }

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val strike = intent.getIntExtra(EXTRA_STRIKE, 1)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                // Give the camera a moment to set exposure, otherwise the first frame is dark.
                window.decorView.postDelayed({ shoot(capture, strike) }, 500)
            }.onFailure { close() }
        }, ContextCompat.getMainExecutor(this))
        // Never stay on screen longer than this, whatever happens.
        window.decorView.postDelayed({ close() }, 6_000)
    }

    private fun shoot(capture: ImageCapture, strike: Int) {
        val file = File(OwnerGuard.photoDir(this), "intruder_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runCatching { OwnerGuard.addIntruder(applicationContext, file, strike) }
                    close()
                }

                override fun onError(exception: ImageCaptureException) {
                    close()
                }
            }
        )
    }

    private fun close() {
        if (done) return
        done = true
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        finish()
        overridePendingTransition(0, 0)
    }
}
