package com.faiza.ai.ai.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.Attachment
import com.faiza.ai.ai.provider.ChatTurn
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.BuiltInSkills
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.SkillException
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The phone's file assistant (STONIC video): find, read, organize, create and send files. */
object FileSkills {

    private const val MAX_RESULTS = 10
    private const val MAX_READ_BYTES = 15L * 1024 * 1024
    private val gson = Gson()

    /** Results of the last search, so the user can say "send number 2". */
    private var lastFound: List<File> = emptyList()
    /** The file made by create_file most recently. */
    private var lastCreated: Pair<String, Uri>? = null

    /** A photo / file made elsewhere (camera) becomes "number 0" for share_file. */
    fun setLastCreated(name: String, uri: Uri) {
        lastCreated = name to uri
    }

    private val root: File get() = Environment.getExternalStorageDirectory()

    private fun folders(which: String?): List<File> {
        val r = root
        val all = listOf(
            "Download", "Documents", "DCIM", "Pictures", "Movies", "Music", "Recordings",
            "Android/media/com.whatsapp/WhatsApp/Media", "WhatsApp/Media"
        ).map { File(r, it) }
        return when (which?.lowercase(Locale.ROOT)) {
            "downloads", "download" -> listOf(File(r, "Download"))
            "documents" -> listOf(File(r, "Documents"))
            "pictures", "photos", "camera" -> listOf(File(r, "DCIM"), File(r, "Pictures"))
            "whatsapp" -> listOf(File(r, "Android/media/com.whatsapp/WhatsApp/Media"), File(r, "WhatsApp/Media"))
            else -> all
        }.filter { it.isDirectory }
    }

    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else {
        FaizaApp.instance.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requireAccess() {
        if (!hasAccess()) throw SkillException(
            "Faiza can't see the phone's files yet. Ask the user to allow \"All files access\" in Faiza AI → Settings → Phone access."
        )
    }

    private val dateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)

    private fun describe(i: Int, f: File) =
        "${i + 1}. ${f.name} — ${FilePlanner.formatSize(f.length())}, ${dateFmt.format(Date(f.lastModified()))}, " +
                "in ${runCatching { f.parentFile?.relativeTo(root)?.path }.getOrNull() ?: f.parent}"

    fun skills(): List<Skill> = listOf(
        Skill(
            "find_files", "🗂️", "Find files", "\"Where's my resume PDF?\"",
            "Search the phone's files (Downloads, Documents, photos, WhatsApp media…) by name and type. Newest first, " +
                    "numbered — use the number with read_file or share_file.",
            listOf(
                ToolParam("query", ParamType.STRING, "Words from the file name (can be empty to list the newest)", required = false),
                ToolParam("kind", ParamType.STRING, "Type of file", required = false,
                    enum = listOf("any", "pdf", "document", "image", "video", "audio", "apk")),
                ToolParam("folder", ParamType.STRING, "Where to look", required = false,
                    enum = listOf("all", "downloads", "documents", "pictures", "whatsapp"))
            )
        ) { _, args -> findFiles(args.str("query").orEmpty(), args.str("kind") ?: "any", args.str("folder")) },

        Skill(
            "read_file", "📖", "Read a file", "\"What does this PDF say?\"",
            "Read or summarize a file from find_files (by number) — PDFs, photos and text files — and answer a question about it.",
            listOf(
                ToolParam("number", ParamType.INTEGER, "File number from find_files"),
                ToolParam("question", ParamType.STRING, "What to find out, e.g. \"summarize it\"", required = false)
            )
        ) { _, args -> readFile(args.int("number") ?: throw SkillException("missing 'number'"), args.str("question")) },

        Skill(
            "organize_folder", "🧹", "Organize files", "\"Organize my Downloads\"",
            "Tidy a folder: move loose files into Images, Videos, Audio, Documents, APKs, Archives and Others. Nothing is " +
                    "deleted. First call without confirmed to get the plan, tell the user, then call with confirmed=true.",
            listOf(
                ToolParam("folder", ParamType.STRING, "downloads, documents, bluetooth, telegram, or a path like Documents/Office"),
                ToolParam("confirmed", ParamType.BOOLEAN, "true after the user agreed to the plan", required = false)
            )
        ) { _, args -> organize(args.require("folder"), args.bool("confirmed")) },

        Skill(
            "undo_organize", "↩️", "Undo organize", "\"Put the files back\"",
            "Put files back where they were before the last organize_folder."
        ) { _, _ -> undoOrganize() },

        Skill(
            "create_file", "📝", "Create files", "\"Make a CSV of these clients\"",
            "Save text you write as a file in Download/Faiza AI: notes (txt/md) or tables (csv: first line = column " +
                    "names, values with commas in \"quotes\"). Then it can be shared with share_file (number 0).",
            listOf(
                ToolParam("name", ParamType.STRING, "File name, e.g. \"International clients\""),
                ToolParam("content", ParamType.STRING, "The full file content"),
                ToolParam("format", ParamType.STRING, "File type (html for web pages, py for Python…)", required = false,
                    enum = listOf("txt", "csv", "md", "html", "css", "js", "py", "json", "xml", "kt", "java"))
            )
        ) { ctx, args -> createFile(ctx, args.require("name"), args.require("content"), args.str("format") ?: "txt") },

        Skill(
            "share_file", "📤", "Send files", "\"Send this PDF to Usman on WhatsApp\"",
            "Send a file (number from find_files, or 0 = the file just made with create_file) to a WhatsApp contact, " +
                    "or open the share menu. For WhatsApp, read back the file and person and get a yes first (confirmed=true).",
            listOf(
                ToolParam("number", ParamType.INTEGER, "File number (0 = last created file)"),
                ToolParam("contact", ParamType.STRING, "WhatsApp contact name or number (empty = share menu)", required = false),
                ToolParam("alternate_names", ParamType.STRING, "Other spellings of the name, comma-separated", required = false),
                ToolParam("confirmed", ParamType.BOOLEAN, "true after the user said yes", required = false)
            )
        ) { ctx, args ->
            shareFile(ctx, args.int("number") ?: 0, args.str("contact"), args.str("alternate_names"), args.bool("confirmed"))
        }
    )

    // ===== Find =====

    private fun kindMatches(kind: String, f: File): Boolean = when (kind) {
        "pdf" -> FilePlanner.extension(f.name) == "pdf"
        "document" -> FilePlanner.category(f.name) == "Documents"
        "image" -> FilePlanner.category(f.name) == "Images"
        "video" -> FilePlanner.category(f.name) == "Videos"
        "audio" -> FilePlanner.category(f.name) == "Audio"
        "apk" -> FilePlanner.category(f.name) == "APKs"
        else -> true
    }

    private suspend fun findFiles(query: String, kind: String, folder: String?): String = withContext(Dispatchers.IO) {
        requireAccess()
        val hits = mutableListOf<File>()
        var scanned = 0
        fun walk(dir: File, depth: Int) {
            if (depth > 6 || scanned > 20_000) return
            val children = dir.listFiles() ?: return
            for (c in children) {
                scanned++
                if (c.name.startsWith(".")) continue
                if (c.isDirectory) walk(c, depth + 1)
                else if (kindMatches(kind, c) && FilePlanner.nameMatches(query, c.name)) hits += c
            }
        }
        folders(folder).forEach { walk(it, 0) }
        lastFound = hits.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }.take(MAX_RESULTS)
        if (lastFound.isEmpty()) "No files found${if (query.isNotBlank()) " matching \"$query\"" else ""}."
        else lastFound.mapIndexed(::describe).joinToString("\n")
    }

    internal fun fileByNumber(n: Int): File =
        lastFound.getOrNull(n - 1) ?: throw SkillException("no file number $n — call find_files first")

    // ===== Read =====

    private suspend fun readFile(number: Int, question: String?): String {
        requireAccess()
        val f = fileByNumber(number)
        val provider = AIProviders.fromPrefs(FaizaApp.instance.prefs) ?: throw SkillException("the AI isn't set up")
        val ask = question ?: "Summarize this file briefly."
        val attachment = withContext(Dispatchers.IO) {
            if (f.length() > MAX_READ_BYTES) throw SkillException("that file is too big to read (max 15 MB)")
            when (FilePlanner.category(f.name)) {
                "Images" -> Attachment(f.name, FilePlanner.mimeType(f.name).takeIf { it.startsWith("image/") } ?: "image/jpeg",
                    base64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                else -> when (FilePlanner.extension(f.name)) {
                    "pdf" -> Attachment(f.name, "application/pdf",
                        base64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                    "txt", "md", "csv", "json", "log", "html", "xml" ->
                        Attachment(f.name, "text/plain", text = f.readText().take(60_000))
                    else -> throw SkillException("I can read PDFs, photos and text files, not .${FilePlanner.extension(f.name)}")
                }
            }
        }
        val reply = provider.chat(
            "You read a file for a phone assistant. Answer the question about it precisely and briefly; plain text.",
            listOf(ChatTurn.User(ask, files = listOf(attachment))), emptyList()
        )
        return reply.text?.trim().orEmpty().ifEmpty { "ERROR: couldn't read the file" }
    }

    // ===== Organize =====

    private data class Move(val from: String, val to: String)

    private fun undoFile(): File = File(FaizaApp.instance.filesDir, "last_organize.json")

    private suspend fun organize(folderName: String, confirmed: Boolean): String = withContext(Dispatchers.IO) {
        requireAccess()
        val dir = FileManager.folder(folderName)
        val children = dir.listFiles() ?: return@withContext "ERROR: can't open the ${dir.name} folder"
        val plan = FilePlanner.organizePlan(children.map { FilePlanner.Entry(it.name, it.isDirectory) })
        val total = plan.values.sumOf { it.size }
        if (total == 0) return@withContext "The ${dir.name} folder is already tidy."
        if (!confirmed) {
            return@withContext "PLAN (nothing moved yet): $total files in ${dir.name}: ${FilePlanner.describePlan(plan)}. " +
                    "Nothing is deleted. Ask the user, then call again with confirmed=true."
        }
        val moves = mutableListOf<Move>()
        for ((cat, names) in plan) {
            val target = File(dir, cat).apply { mkdirs() }
            for (name in names) {
                val src = File(dir, name)
                val dest = File(target, FilePlanner.freeName(name) { File(target, it).exists() })
                if (src.renameTo(dest)) moves += Move(src.absolutePath, dest.absolutePath)
            }
        }
        undoFile().writeText(gson.toJson(moves))
        "OK: moved ${moves.size} of $total files in ${dir.name} into folders (${FilePlanner.describePlan(plan)}). " +
                "The user can say \"undo\" to put them back."
    }

    private suspend fun undoOrganize(): String = withContext(Dispatchers.IO) {
        requireAccess()
        val f = undoFile()
        if (!f.exists()) return@withContext "There's nothing to undo."
        val moves: List<Move> = runCatching {
            gson.fromJson<List<Move>>(f.readText(), object : TypeToken<List<Move>>() {}.type)
        }.getOrNull().orEmpty()
        var back = 0
        for (m in moves.asReversed()) {
            val now = File(m.to)
            val orig = File(m.from)
            if (now.exists() && !orig.exists() && now.renameTo(orig)) back++
        }
        // Remove the category folders the organize created, if they're empty now.
        moves.mapNotNull { File(it.to).parentFile }.distinct().forEach { d -> if (d.listFiles()?.isEmpty() == true) d.delete() }
        f.delete()
        "OK: put $back files back where they were."
    }

    // ===== Create =====

    internal suspend fun createFile(ctx: Context, name: String, content: String, format: String): String = withContext(Dispatchers.IO) {
        val ext = if (format in setOf("txt", "csv", "md", "html", "css", "js", "py", "json", "xml", "kt", "java")) format else "txt"
        val fileName = FilePlanner.safeFileName(name, ext)
        val bytes = (if (ext == "csv") "﻿" else "").toByteArray() + content.toByteArray(Charsets.UTF_8) // BOM: Excel reads Bangla
        val uri: Uri = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, FilePlanner.mimeType(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Faiza AI")
            }
            val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw SkillException("couldn't create the file")
            ctx.contentResolver.openOutputStream(u)?.use { it.write(bytes) } ?: throw SkillException("couldn't write the file")
            u
        } else {
            val dir = File(root, "Download/Faiza AI").apply { mkdirs() }
            val file = File(dir, FilePlanner.freeName(fileName) { File(dir, it).exists() }).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        }
        lastCreated = fileName to uri
        "OK: saved \"$fileName\" in Download/Faiza AI. To send it, use share_file with number 0."
    }

    // ===== Share =====

    private suspend fun shareFile(ctx: Context, number: Int, contact: String?, alternates: String?, confirmed: Boolean): String {
        val (name, uri) = if (number == 0) {
            lastCreated ?: throw SkillException("no file was created yet — use find_files and a number")
        } else {
            requireAccess()
            val f = fileByNumber(number)
            f.name to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        }
        val mime = FilePlanner.mimeType(name)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (contact.isNullOrBlank()) {
            ctx.startActivity(Intent.createChooser(send, "Send $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "OK: the share menu is open for \"$name\" — the user picks the app."
        }
        if (!confirmed) {
            return "NOT SENT. First tell the user: send \"$name\" to $contact on WhatsApp? Only after they say yes, call again with confirmed=true."
        }
        val (person, jid) = BuiltInSkills.whatsappJid(ctx, contact, alternates)
            ?: return "ERROR: no contact named \"$contact\". Ask for another spelling or the number."
        val opened = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
            try {
                ctx.startActivity(Intent(send).setPackage(pkg).putExtra("jid", jid))
                true
            } catch (_: android.content.ActivityNotFoundException) {
                false
            }
        }
        if (!opened) return "ERROR: WhatsApp is not installed"
        delay(3_000)
        val a = FaizaAccessibilityService.instance
            ?: return "OK: WhatsApp is open with \"$name\" ready for $person. The user needs to tap Send."
        val sent = BuiltInSkills.sendLabels.any { a.clickByDescription(it) } ||
                (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
        return if (sent) "OK: sent \"$name\" to $person on WhatsApp."
        else "OK: WhatsApp shows \"$name\" for $person, but I couldn't find the Send button — use read_screen and tap it."
    }
}
