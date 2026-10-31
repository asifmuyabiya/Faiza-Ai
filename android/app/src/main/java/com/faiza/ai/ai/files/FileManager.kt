package com.faiza.ai.ai.files

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.ContactFinder
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.SkillException
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File & app manager (owner: "organise my files and apps, with my permission"). Reports, tidies any folder, finds
 * duplicates, moves/renames, and "deletes" only into Faiza Bin (restorable). Apps: list, open their settings
 * (clear cache / storage / permissions), uninstall through Android's own confirmation.
 */
object FileManager {

    private val gson = Gson()
    private val root: File get() = Environment.getExternalStorageDirectory()
    private val bin: File get() = File(root, "Faiza Bin").apply { mkdirs() }
    private val binIndex: File get() = File(bin, ".index.json")

    private val SCAN_ROOTS = listOf(
        "Download", "Documents", "DCIM", "Pictures", "Movies", "Music", "Recordings", "Bluetooth", "Telegram",
        "Android/media/com.whatsapp/WhatsApp/Media", "WhatsApp/Media"
    )

    /** Last duplicate search, so "move the duplicates to the bin" acts on exactly what was reported. */
    private var lastDuplicates: List<List<FileOps.Seen>> = emptyList()

    private fun rel(f: File) = runCatching { f.relativeTo(root).path }.getOrDefault(f.path)

    private fun scan(dirs: List<File>, limit: Int = 25_000): List<File> {
        val out = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            if (out.size >= limit || depth > 8) return
            d.listFiles()?.forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                if (f.isDirectory) { if (!FileOps.isProtected(rel(f)) || rel(f).lowercase() == "dcim/camera") walk(f, depth + 1) }
                else out += f
            }
        }
        dirs.filter { it.isDirectory }.forEach { walk(it, 0) }
        return out
    }

    /** Folder from a name ("downloads", "whatsapp", "Documents/Office") — never a protected one. */
    internal fun folder(name: String?): File {
        val n = name?.trim().orEmpty()
        val alias = when (n.lowercase()) {
            "", "downloads", "download" -> "Download"
            "documents", "docs" -> "Documents"
            "pictures", "photos" -> "Pictures"
            "music", "audio" -> "Music"
            "movies", "videos" -> "Movies"
            "bluetooth" -> "Bluetooth"
            "telegram" -> "Telegram"
            else -> n.trim('/')
        }
        if (FileOps.isProtected(alias)) throw SkillException("that folder is protected (system or camera roll) — Faiza won't rearrange it")
        val f = File(root, alias)
        if (!f.isDirectory) throw SkillException("there's no folder \"$alias\" — try downloads, documents, pictures, music, movies or a path like Documents/Office")
        return f
    }

    private fun need() {
        if (!FileSkills.hasAccess()) throw SkillException(
            "Faiza can't see the phone's files yet. Ask the user to allow \"All files access\" in Faiza AI → Settings → Phone access."
        )
    }

    private fun rescan(ctx: Context, vararg paths: String) =
        runCatching { MediaScannerConnection.scanFile(ctx, paths, null, null) }

    private fun hash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(f).use { input ->
            val buf = ByteArray(64 * 1024)
            if (f.length() <= 200L * 1024 * 1024) {
                while (true) { val r = input.read(buf); if (r <= 0) break; md.update(buf, 0, r) }
            } else { // very big: first and last 4 MB are enough to tell copies apart
                var left = 4 * 1024 * 1024
                while (left > 0) { val r = input.read(buf, 0, minOf(buf.size, left)); if (r <= 0) break; md.update(buf, 0, r); left -= r }
                input.channel.position(f.length() - 4L * 1024 * 1024)
                while (true) { val r = input.read(buf); if (r <= 0) break; md.update(buf, 0, r) }
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    // ===== Reports =====

    private suspend fun report(): String = withContext(Dispatchers.IO) {
        need()
        val st = StatFs(root.path)
        val total = st.totalBytes
        val free = st.availableBytes
        val files = scan(SCAN_ROOTS.map { File(root, it) })
        val seen = files.map { FileOps.Seen(rel(it), it.length(), it.lastModified()) }
        val cats = FileOps.byCategory(seen).take(7)
        val biggest = seen.sortedByDescending { it.size }.take(5)
        val apks = FileOps.oldApks(seen.filter { it.path.startsWith("Download") }, System.currentTimeMillis())
        "STORAGE: ${FilePlanner.formatSize(total - free)} used of ${FilePlanner.formatSize(total)} " +
                "(${FilePlanner.formatSize(free)} free).\nBy type: " +
                cats.joinToString("; ") { "${it.first} ${it.second} files ${FilePlanner.formatSize(it.third)}" } +
                "\nBiggest files: " + biggest.joinToString("; ") { "${it.path} (${FilePlanner.formatSize(it.size)})" } +
                (if (apks.isNotEmpty()) "\nOld installer APKs in Download: ${apks.size} (${FilePlanner.formatSize(apks.sumOf { it.size })}) — usually safe to bin." else "") +
                "\nSuggest the user's next step (organise a folder, find duplicates, bin old APKs) in one or two sentences."
    }

    private suspend fun findDuplicates(where: String?): String = withContext(Dispatchers.IO) {
        need()
        val dirs = if (where.isNullOrBlank()) SCAN_ROOTS.map { File(root, it) } else listOf(folder(where))
        val files = scan(dirs).filter { it.length() > 0 }
        // Only files sharing a size can be copies: hash just those.
        val candidates = files.groupBy { it.length() }.values.filter { it.size > 1 }.flatten()
        val seen = candidates.map { FileOps.Seen(rel(it), it.length(), it.lastModified(), hash(it)) }
        lastDuplicates = FileOps.duplicateGroups(seen)
        if (lastDuplicates.isEmpty()) return@withContext "No duplicate files found (${files.size} files checked)."
        "DUPLICATES: ${lastDuplicates.size} groups, ${FilePlanner.formatSize(FileOps.wasted(lastDuplicates))} could be freed. " +
                lastDuplicates.take(8).joinToString("; ") { g ->
                    "${g.first().path.substringAfterLast('/')} ×${g.size} (${FilePlanner.formatSize(g.first().size)} each)"
                } + ". The oldest copy of each is kept. Ask the user, then call bin_duplicates with confirmed=true."
    }

    private suspend fun binDuplicates(ctx: Context, confirmed: Boolean): String = withContext(Dispatchers.IO) {
        need()
        if (lastDuplicates.isEmpty()) return@withContext "ERROR: call find_duplicates first"
        val extra = lastDuplicates.flatMap { it.drop(1) }
        if (!confirmed) return@withContext "NOT DONE: ${extra.size} extra copies would go to Faiza Bin. Ask the user, then confirmed=true."
        val n = extra.count { toBin(ctx, File(root, it.path)) }
        lastDuplicates = emptyList()
        "OK: moved $n extra copies to Faiza Bin (one copy of each file kept). They can be restored with restore_from_bin."
    }

    // ===== Bin =====

    private fun index(): MutableMap<String, String> = runCatching {
        gson.fromJson<MutableMap<String, String>>(binIndex.readText(), object : TypeToken<MutableMap<String, String>>() {}.type)
    }.getOrNull() ?: mutableMapOf()

    private fun toBin(ctx: Context, f: File): Boolean {
        if (!f.exists()) return false
        val parent = rel(f.parentFile ?: f).lowercase()
        if (parent.startsWith("android/data") || parent.startsWith("android/obb")) return false
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH).format(Date())
        val dest = File(bin, FileOps.binName(f.name, stamp + "-" + (f.hashCode() and 0xfff)))
        val from = f.absolutePath
        if (!f.renameTo(dest)) return false
        val idx = index(); idx[dest.name] = from; binIndex.writeText(gson.toJson(idx))
        rescan(ctx, from, dest.absolutePath)
        return true
    }

    private suspend fun restore(ctx: Context, name: String?): String = withContext(Dispatchers.IO) {
        need()
        val idx = index()
        val picks = idx.keys.filter { name.isNullOrBlank() || FileOps.originalName(it).contains(name, ignoreCase = true) }
        if (picks.isEmpty()) return@withContext "Nothing in Faiza Bin matches."
        var n = 0
        for (k in picks) {
            val src = File(bin, k)
            val dest = File(idx[k]!!).let { d ->
                if (!d.exists()) d else File(d.parentFile, FilePlanner.freeName(d.name) { File(d.parentFile, it).exists() })
            }
            dest.parentFile?.mkdirs()
            if (src.renameTo(dest)) { n++; idx.remove(k); rescan(ctx, src.absolutePath, dest.absolutePath) }
        }
        binIndex.writeText(gson.toJson(idx))
        "OK: restored $n file(s) to where they were."
    }

    private fun binList(): String {
        val idx = index()
        if (idx.isEmpty()) return "Faiza Bin is empty."
        val size = idx.keys.sumOf { File(bin, it).length() }
        return "Faiza Bin: ${idx.size} file(s), ${FilePlanner.formatSize(size)}: " +
                idx.keys.take(15).joinToString(", ") { FileOps.originalName(it) } +
                ". To free the space for good, the user can empty the \"Faiza Bin\" folder in their file manager."
    }

    // ===== Single files (numbers from find_files) =====

    private suspend fun moveFile(ctx: Context, number: Int, to: String): String = withContext(Dispatchers.IO) {
        need()
        val f = FileSkills.fileByNumber(number)
        val rel = to.trim('/')
        if (FileOps.isProtected(rel)) throw SkillException("that folder is protected")
        val dir = File(root, rel).apply { mkdirs() }
        val dest = File(dir, FilePlanner.freeName(f.name) { File(dir, it).exists() })
        if (!f.renameTo(dest)) return@withContext "ERROR: couldn't move ${f.name}"
        rescan(ctx, f.absolutePath, dest.absolutePath)
        "OK: moved ${f.name} to ${rel(dir)}"
    }

    private suspend fun renameFile(ctx: Context, number: Int, newName: String): String = withContext(Dispatchers.IO) {
        need()
        val f = FileSkills.fileByNumber(number)
        val ext = f.name.substringAfterLast('.', "")
        val clean = FilePlanner.safeFileName(newName, ext)
        val dest = File(f.parentFile, FilePlanner.freeName(clean) { File(f.parentFile, it).exists() })
        if (!f.renameTo(dest)) return@withContext "ERROR: couldn't rename ${f.name}"
        rescan(ctx, f.absolutePath, dest.absolutePath)
        "OK: renamed ${f.name} → ${dest.name}"
    }

    private suspend fun binFiles(ctx: Context, numbers: String, confirmed: Boolean): String = withContext(Dispatchers.IO) {
        need()
        val files = numbers.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }.distinct().map { FileSkills.fileByNumber(it) }
        if (files.isEmpty()) throw SkillException("give file numbers from find_files, e.g. \"1, 3\"")
        if (!confirmed) return@withContext "NOT DONE: ${files.joinToString { it.name }} would go to Faiza Bin (restorable). Ask the user, then confirmed=true."
        val n = files.count { toBin(ctx, it) }
        "OK: moved $n file(s) to Faiza Bin. Say \"restore\" to bring them back."
    }

    // ===== Apps =====

    private data class App(val label: String, val pkg: String)

    private fun apps(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    private fun app(ctx: Context, name: String): App =
        apps(ctx).map { it to ContactFinder.scoreName(it.label, name) }.filter { it.second >= 60 }.maxByOrNull { it.second }?.first
            ?: throw SkillException("no app called \"$name\" on this phone")

    private fun listApps(ctx: Context): String {
        val pm = ctx.packageManager
        val list = apps(ctx)
        val user = list.filter { a ->
            runCatching { (pm.getApplicationInfo(a.pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }.getOrDefault(true)
        }
        return "${list.size} apps (${user.size} installed by the user): " + user.joinToString(", ") { it.label }
    }

    fun skills(): List<Skill> = listOf(
        Skill("storage_report", "💾", "Storage report", "\"আমার ফোনে জায়গা কিসে খাচ্ছে?\"",
            "How full the phone's storage is, what takes the space (photos, videos, documents…), the biggest files and old installer APKs.",
            emptyList()) { _, _ -> report() },
        Skill("find_duplicates", "🧬", "Find duplicate files", "\"ডুপ্লিকেট ফাইল খোঁজো\"",
            "Find identical copies of files (checked by content, not just name), in one folder or everywhere.",
            listOf(ToolParam("folder", ParamType.STRING, "downloads, pictures, whatsapp… (empty = everywhere)", required = false))
        ) { _, args -> findDuplicates(args.str("folder")) },
        Skill("bin_duplicates", "🗑️", "Remove duplicates", "Keeps one copy of each",
            "Move the extra copies found by find_duplicates to Faiza Bin (restorable), keeping the oldest copy. Ask first.",
            listOf(ToolParam("confirmed", ParamType.BOOLEAN, "true after the user agreed", required = false))
        ) { ctx, args -> binDuplicates(ctx, args.bool("confirmed")) },
        Skill("move_file", "📦", "Move a file", "\"এই ফাইলটা Documents/Office-এ রাখো\"",
            "Move a file (number from find_files) into a folder (created if needed), e.g. \"Documents/Office\".",
            listOf(ToolParam("number", ParamType.INTEGER, "File number from find_files"),
                ToolParam("to_folder", ParamType.STRING, "Folder path from storage, e.g. Documents/Office"))
        ) { ctx, args -> moveFile(ctx, (args.get("number")?.asInt ?: 0), args.require("to_folder")) },
        Skill("rename_file", "✏️", "Rename a file", "\"নাম দাও passport_scan\"",
            "Rename a file (number from find_files). The extension is kept.",
            listOf(ToolParam("number", ParamType.INTEGER, "File number from find_files"),
                ToolParam("new_name", ParamType.STRING, "New name"))
        ) { ctx, args -> renameFile(ctx, (args.get("number")?.asInt ?: 0), args.require("new_name")) },
        Skill("bin_files", "🗑️", "Delete files (to bin)", "\"১ আর ৩ নম্বর ফাইল ডিলিট করো\"",
            "\"Delete\" files by moving them to Faiza Bin — nothing is destroyed and they can be restored. Ask first.",
            listOf(ToolParam("numbers", ParamType.STRING, "File numbers from find_files, e.g. \"1, 3\""),
                ToolParam("confirmed", ParamType.BOOLEAN, "true after the user agreed", required = false))
        ) { ctx, args -> binFiles(ctx, args.require("numbers"), args.bool("confirmed")) },
        Skill("restore_from_bin", "♻️", "Restore files", "\"ডিলিট করা ফাইল ফেরত আনো\"",
            "Put files from Faiza Bin back where they were (all, or those whose name contains the given text).",
            listOf(ToolParam("name", ParamType.STRING, "Part of the file name (empty = everything)", required = false))
        ) { ctx, args -> restore(ctx, args.str("name")) },
        Skill("faiza_bin", "🗑️", "What's in the bin", "\"বিনে কী আছে?\"",
            "List what is in Faiza Bin.", emptyList()) { _, _ -> binList() },
        Skill("list_apps", "📲", "My apps", "\"আমার ফোনে কী কী অ্যাপ আছে?\"",
            "List the apps on the phone (those the user installed, by name).", emptyList()) { ctx, _ -> listApps(ctx) },
        Skill("app_settings", "⚙️", "App settings", "\"WhatsApp-এর cache clear করো\"",
            "Open an app's Android settings page (storage & cache, permissions, notifications, force stop). Then use " +
                    "read_screen / tap_on to do what the user asked there, e.g. Storage → Clear cache.",
            listOf(ToolParam("app", ParamType.STRING, "App name"))
        ) { ctx, args ->
            val a = app(ctx, args.require("app"))
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            kotlinx.coroutines.delay(1_200)
            "OK: opened the settings of ${a.label}. Use read_screen to continue (Storage & cache → Clear cache, Permissions…)."
        },
        Skill("uninstall_app", "🧺", "Uninstall an app", "\"Likee আনইনস্টল করো\"",
            "Uninstall an app: Android shows its own confirmation, which the user taps. Ask the user first.",
            listOf(ToolParam("app", ParamType.STRING, "App name"),
                ToolParam("confirmed", ParamType.BOOLEAN, "true after the user agreed", required = false))
        ) { ctx, args ->
            val a = app(ctx, args.require("app"))
            if (!args.bool("confirmed")) return@Skill "NOT DONE: ask the user to confirm uninstalling ${a.label}, then confirmed=true."
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "OK: Android is asking to uninstall ${a.label} — the user taps OK on the phone to finish."
        }
    )

}
