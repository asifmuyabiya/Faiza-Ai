package com.faiza.ai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import com.faiza.ai.ai.provider.Attachment
import java.io.ByteArrayOutputStream

/** Turns a picked file into something the AI can read (video 30:51: photos, PDFs, text files). */
object AttachmentReader {

    const val MAX_PDF_BYTES = 15L * 1024 * 1024
    const val MAX_TEXT_CHARS = 60_000
    private const val MAX_IMAGE_SIDE = 1536

    class UnsupportedFileException(message: String) : Exception(message)

    enum class Kind { IMAGE, PDF, TEXT, UNSUPPORTED }

    private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "kt", "java", "py", "js")

    /** Pure, so it is unit-tested. */
    fun kindOf(mimeType: String?, name: String): Kind {
        val mime = mimeType.orEmpty().lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp") -> Kind.IMAGE
            mime == "application/pdf" || ext == "pdf" -> Kind.PDF
            mime.startsWith("text/") || mime == "application/json" || ext in TEXT_EXTENSIONS -> Kind.TEXT
            else -> Kind.UNSUPPORTED
        }
    }

    /** Throws [UnsupportedFileException] with a message to show the user. Call off the main thread. */
    fun read(ctx: Context, uri: Uri): Attachment {
        val cr = ctx.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = cr.getType(uri)
        return when (kindOf(mime, name)) {
            Kind.IMAGE -> Attachment(name, "image/jpeg", base64 = imageJpeg(ctx, uri))
            Kind.PDF -> {
                if (size > MAX_PDF_BYTES) throw UnsupportedFileException("That PDF is too big (max 15 MB).")
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw UnsupportedFileException("Couldn't open that file.")
                if (bytes.size > MAX_PDF_BYTES) throw UnsupportedFileException("That PDF is too big (max 15 MB).")
                Attachment(name, "application/pdf", base64 = Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            Kind.TEXT -> {
                val text = cr.openInputStream(uri)?.use { input ->
                    String(input.readNBytesCompat(MAX_TEXT_CHARS * 4), Charsets.UTF_8)
                } ?: throw UnsupportedFileException("Couldn't open that file.")
                val clipped = if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) + "\n[…file cut off here]" else text
                Attachment(name, "text/plain", text = clipped)
            }
            Kind.UNSUPPORTED -> throw UnsupportedFileException("Faiza can read photos, PDFs and text files.")
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Any photo (HEIC, PNG…) → JPEG at most 1536 px, so it uploads fast. */
    private fun imageJpeg(ctx: Context, uri: Uri): String {
        val bmp: Bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(w, h))
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw UnsupportedFileException("Couldn't open that photo.")
            }
        } catch (e: UnsupportedFileException) {
            throw e
        } catch (e: Exception) {
            throw UnsupportedFileException("Couldn't open that photo.")
        }
        return jpegBase64(bmp, MAX_IMAGE_SIDE, 82)
    }

    fun jpegBase64(bmp: Bitmap, maxSide: Int, quality: Int): String =
        Base64.encodeToString(jpegBytes(bmp, maxSide, quality), Base64.NO_WRAP)

    fun jpegBytes(bmp: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
