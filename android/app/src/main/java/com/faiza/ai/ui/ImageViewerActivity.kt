package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows a picture Faiza created (video 29:43), with share. */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMPT = "prompt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)
        val uri = intent.data ?: run { finish(); return }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvPrompt).text = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
        findViewById<View>(R.id.btnShare).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
                .setType(contentResolver.getType(uri) ?: "image/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Share picture"))
        }

        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { load(uri) }
            if (bmp == null) {
                findViewById<TextView>(R.id.tvPrompt).text = "Couldn't open the picture."
            } else {
                findViewById<ImageView>(R.id.image).setImageBitmap(bmp)
            }
        }
    }

    private fun load(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                d.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    }.getOrNull()
}
