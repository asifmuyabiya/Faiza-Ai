package com.faiza.ai.ai.files

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Files Faiza saved in Download/Faiza AI (code from the Coding room, reports, tables) — listed so the owner can
 * open or delete them (owner: "বানানো ডিজাইনগুলো ডিলিট করা যায় না").
 */
object SavedFiles {

    data class Item(val name: String, val size: Long, val modified: Long, val uri: Uri, val file: File?)

    private const val FOLDER = "Faiza AI"

    fun list(ctx: Context): List<Item> {
        if (Build.VERSION.SDK_INT >= 29) {
            val out = mutableListOf<Item>()
            runCatching {
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_MODIFIED),
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?", arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$FOLDER%"),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        out += Item(c.getString(1), c.getLong(2), c.getLong(3) * 1000,
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)), null)
                    }
                }
            }
            return out
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        return (dir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedByDescending { it.lastModified() }
            .map { Item(it.name, it.length(), it.lastModified(), Uri.fromFile(it), it) }
    }

    /** True if it's gone. Faiza can always delete files she created herself. */
    fun delete(ctx: Context, item: Item): Boolean = runCatching {
        if (item.file != null) item.file.delete() || !item.file.exists()
        else ctx.contentResolver.delete(item.uri, null, null) > 0
    }.getOrDefault(false)

    fun sizeText(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
