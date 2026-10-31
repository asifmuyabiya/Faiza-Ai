package com.faiza.ai.ai.files

import java.text.Normalizer
import java.util.Locale

/** Pure helpers for the file assistant (STONIC video: "organize the files", "make a CSV"). Unit-tested. */
object FilePlanner {

    val CATEGORIES = listOf("Images", "Videos", "Audio", "Documents", "APKs", "Archives", "Others")

    private val TYPES = mapOf(
        "Images" to setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "svg"),
        "Videos" to setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v"),
        "Audio" to setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr"),
        "Documents" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "odt", "json", "html", "epub"),
        "APKs" to setOf("apk", "apks", "xapk"),
        "Archives" to setOf("zip", "rar", "7z", "tar", "gz")
    )

    /** Downloads still in progress — never move these. */
    private val UNFINISHED = setOf("crdownload", "part", "tmp", "download", "partial")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun category(name: String): String {
        val ext = extension(name)
        return TYPES.entries.firstOrNull { ext in it.value }?.key ?: "Others"
    }

    fun mimeType(name: String): String = when (extension(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt", "md", "log" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }

    data class Entry(val name: String, val isDirectory: Boolean, val hidden: Boolean = name.startsWith("."))

    /** Which loose files go into which folder. Folders, hidden files and unfinished downloads stay put. */
    fun organizePlan(entries: List<Entry>): Map<String, List<String>> =
        entries.filter { !it.isDirectory && !it.hidden && extension(it.name) !in UNFINISHED && it.name !in CATEGORIES }
            .groupBy({ category(it.name) }, { it.name })
            .toSortedMap(compareBy { CATEGORIES.indexOf(it) })

    fun describePlan(plan: Map<String, List<String>>): String =
        plan.entries.joinToString(", ") { (cat, files) -> "${files.size} → $cat" }

    /** "report.pdf" → "report (1).pdf" when the name is taken. */
    fun freeName(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (taken("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        return buildString {
            for (ch in n) {
                val t = Character.getType(ch)
                if (ch.isLetterOrDigit() || t == Character.NON_SPACING_MARK.toInt() ||
                    t == Character.COMBINING_SPACING_MARK.toInt()
                ) append(ch) else append(' ')
            }
        }.replace(Regex(" +"), " ").trim()
    }

    /** All the words of [query] appear in the file name ("resume pdf" finds "My_Resume_2026.pdf"). */
    fun nameMatches(query: String, fileName: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val name = normalize(fileName)
        val ext = extension(fileName)
        return words.all { w -> name.contains(w) || w == ext }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(Locale.ENGLISH, bytes / 1048576.0)
        else -> "%.1f GB".format(Locale.ENGLISH, bytes / 1073741824.0)
    }

    /** A safe file name from what the AI suggested. */
    fun safeFileName(name: String, defaultExt: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").take(80).ifBlank { "Faiza note" }
        return if (extension(cleaned).isEmpty()) "$cleaned.$defaultExt" else cleaned
    }
}
