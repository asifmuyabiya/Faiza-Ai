package com.faiza.ai.ai.code

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One Code Studio project: a folder of files plus a small meta.json. */
class CodeProject(val dir: File) {

    data class Meta(
        var name: String = "",
        var kind: String = "WEB",
        var plan: String = "",
        var requests: MutableList<String> = mutableListOf(),
        var status: String = "",
        var rounds: Int = 0,
        var createdAt: Long = System.currentTimeMillis(),
        var updatedAt: Long = System.currentTimeMillis()
    )

    val slug: String get() = dir.name
    private val metaFile get() = File(dir, ".faiza/meta.json")

    var meta: Meta = runCatching { gson.fromJson(metaFile.readText(), Meta::class.java) }.getOrNull() ?: Meta(name = dir.name)
        private set

    val kind: CodeParsers.Kind get() = runCatching { CodeParsers.Kind.valueOf(meta.kind) }.getOrDefault(CodeParsers.Kind.WEB)

    fun saveMeta(change: Meta.() -> Unit = {}) {
        meta.change()
        meta.updatedAt = System.currentTimeMillis()
        metaFile.parentFile?.mkdirs()
        metaFile.writeText(gson.toJson(meta))
    }

    /** Relative paths of the project's own files (not the .faiza folder, not node_modules etc.). */
    fun files(): List<String> = dir.walkTopDown()
        .onEnter { it == dir || it.name !in SKIP_DIRS }
        .filter { it.isFile }
        .map { it.relativeTo(dir).path.replace('\\', '/') }
        .sorted()
        .toList()

    fun read(path: String): String? = CodeParsers.safePath(path)?.let { File(dir, it) }?.takeIf { it.isFile }?.readText()

    fun write(path: String, content: String) {
        val p = CodeParsers.safePath(path) ?: return
        val f = File(dir, p)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    fun delete(path: String) {
        CodeParsers.safePath(path)?.let { File(dir, it).delete() }
    }

    fun apply(edits: CodeParsers.Edits): Int {
        edits.files.forEach { (p, c) -> write(p, c) }
        edits.deletes.forEach { delete(it) }
        return edits.files.size + edits.deletes.size
    }

    /** Text files with their content, for prompts. */
    fun textFiles(): List<Pair<String, String>> = files()
        .filter { CodeParsers.isText(it) && File(dir, it).length() < 400_000 }
        .map { it to (read(it) ?: "") }

    /** Zips the project into Downloads/Faiza Code; returns where it went. */
    fun exportZip(ctx: Context): String {
        val name = "${slug}.zip"
        val write: (java.io.OutputStream) -> Unit = { out ->
            ZipOutputStream(out).use { zip ->
                files().forEach { p ->
                    zip.putNextEntry(ZipEntry(p))
                    File(dir, p).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Faiza Code")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("couldn't create the zip in Downloads")
            ctx.contentResolver.openOutputStream(uri)!!.use(write)
        } else {
            @Suppress("DEPRECATION")
            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Faiza Code").apply { mkdirs() }
            File(d, name).outputStream().use(write)
        }
        return "Downloads/Faiza Code/$name"
    }

    companion object {
        private val gson = Gson()
        val SKIP_DIRS = setOf(".faiza", "node_modules", "__pycache__", ".git", ".venv", "venv", ".pytest_cache")
    }
}

object CodeProjects {

    /**
     * Shared storage ("Faiza Code" folder) when Faiza has all-files access, so Termux and the file manager can
     * reach the projects; otherwise Faiza's own folder (websites still work, Termux can't see it).
     */
    fun root(ctx: Context): File {
        val shared = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        val r = if (shared) File(Environment.getExternalStorageDirectory(), "Faiza Code")
        else File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Faiza Code")
        r.mkdirs()
        return r
    }

    fun sharedStorage(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun list(ctx: Context): List<CodeProject> = (root(ctx).listFiles() ?: emptyArray())
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .map { CodeProject(it) }
        .sortedByDescending { it.meta.updatedAt }

    fun create(ctx: Context, name: String): CodeProject {
        val base = CodeParsers.slug(name)
        var slug = base
        var n = 2
        while (File(root(ctx), slug).exists()) slug = "$base-${n++}"
        val p = CodeProject(File(root(ctx), slug).apply { mkdirs() })
        p.saveMeta { this.name = name }
        return p
    }

    /** By folder name or project name (loosely). */
    fun find(ctx: Context, key: String?): CodeProject? {
        if (key.isNullOrBlank()) return null
        val all = list(ctx)
        val k = key.trim().lowercase()
        return all.firstOrNull { it.slug == CodeParsers.slug(key) }
            ?: all.firstOrNull { it.meta.name.lowercase() == k }
            ?: all.firstOrNull { it.meta.name.lowercase().contains(k) || it.slug.contains(CodeParsers.slug(key)) }
    }
}
