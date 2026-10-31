package com.faiza.ai.ai.voice

import android.content.Context
import android.os.Environment
import android.util.Base64
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.files.FileSkills
import com.faiza.ai.ai.provider.Attachment
import com.faiza.ai.ai.provider.ChatTurn
import com.faiza.ai.ai.provider.GeminiProvider
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ProviderType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.str
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WhatsApp voice messages → text (owner: "লম্বা ভয়েস মেসেজ না শুনেও ফাইজা লিখে দেবে আর ছোট করে বলবে").
 * The audio files are in WhatsApp's media folder; who sent which one comes from the "🎤 Voice message" notifications.
 */
object VoiceNotes {

    data class Heard(val sender: String, val app: String, val at: Long)
    data class Note(val file: File, val sender: String?, val at: Long)

    private val prefs get() = FaizaApp.instance.prefs
    private val gson = Gson()
    private val heard = mutableListOf<Heard>()
    private var loaded = false

    private val FOLDERS = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes",
        "WhatsApp/Media/WhatsApp Voice Notes",
        "WhatsApp Business/Media/WhatsApp Business Voice Notes"
    )

    private val VOICE_WORDS = listOf("voice message", "voice note", "ভয়েস মেসেজ", "ভয়েস মেসেজ", "ভয়েস বার্তা", "वॉइस मैसेज", "🎤")

    fun isVoiceText(text: String): Boolean = VOICE_WORDS.any { text.contains(it, ignoreCase = true) }

    /** "Family: Ammu" (group) → "Ammu"; "Ammu (2 messages)" → "Ammu". */
    fun cleanSender(title: String): String =
        title.substringAfterLast(": ").replace(Regex("\\s*\\(\\d+.*\\)$"), "").trim()

    /** From the notification listener. */
    fun onNotification(pkg: String, title: String, text: String, at: Long) {
        if (!pkg.startsWith("com.whatsapp") || !isVoiceText(text) || title.isBlank()) return
        load()
        synchronized(heard) {
            heard.add(0, Heard(cleanSender(title), if (pkg.endsWith("w4b")) "WhatsApp Business" else "WhatsApp", at))
            while (heard.size > 60) heard.removeAt(heard.lastIndex)
            prefs.voiceNoteLog = gson.toJson(heard)
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val list: List<Heard> = gson.fromJson(prefs.voiceNoteLog ?: "[]", object : TypeToken<List<Heard>>() {}.type)
            synchronized(heard) { heard.clear(); heard.addAll(list) }
        }
    }

    fun audioFiles(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        return FOLDERS.map { File(root, it) }.filter { it.isDirectory }.flatMap { dir ->
            dir.walkTopDown().maxDepth(3).filter {
                it.isFile && it.extension.lowercase() in setOf("opus", "ogg", "m4a", "aac", "mp3") && it.length() > 0
            }.toList()
        }.sortedByDescending { it.lastModified() }
    }

    /**
     * Pairs each notification with the audio file saved closest to its time (WhatsApp saves the file a moment
     * before or after the notification). Pure, for tests.
     */
    fun match(files: List<Pair<String, Long>>, notes: List<Heard>, windowMs: Long = 3 * 60_000): Map<String, Heard> {
        val out = HashMap<String, Heard>()
        val used = HashSet<String>()
        notes.sortedByDescending { it.at }.forEach { n ->
            files.filter { it.first !in used && kotlin.math.abs(it.second - n.at) <= windowMs }
                .minByOrNull { kotlin.math.abs(it.second - n.at) }
                ?.let { used += it.first; out[it.first] = n }
        }
        return out
    }

    /** Newest voice notes, optionally only from [who] (name or nickname, loosely). */
    fun find(who: String?, count: Int): List<Note> {
        load()
        val files = audioFiles().take(80)
        val notes = synchronized(heard) { heard.toList() }
        val senders = match(files.map { it.absolutePath to it.lastModified() }, notes)
        val all = files.map { f -> Note(f, senders[f.absolutePath]?.sender, f.lastModified()) }
        val picked = if (who.isNullOrBlank()) all else {
            val names = nameVariants(who)
            all.filter { n -> n.sender != null && names.any { v -> n.sender.contains(v, true) || v.contains(n.sender, true) } }
        }
        return picked.take(count.coerceIn(1, 5))
    }

    /** The name plus My People nicknames/real name for it ("আম্মু" → "Ammu", "Mom"…). */
    private fun nameVariants(who: String): List<String> {
        val people = runCatching { com.faiza.ai.ai.contacts.FavoriteContacts.load(prefs) }.getOrDefault(emptyList())
        val p = people.firstOrNull { c -> c.allNames.any { it.equals(who.trim(), true) || it.contains(who.trim(), true) } }
        return (listOf(who.trim()) + (p?.allNames ?: emptyList())).filter { it.length >= 2 }.distinct()
    }

    private suspend fun transcribe(f: File, bangla: Boolean): String {
        val key = prefs.geminiApiKey?.trim().orEmpty()
        if (key.isEmpty()) return "ERROR: needs a Gemini API key"
        if (f.length() > 15_000_000) return "(too long to read — over 15 MB)"
        val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        val mime = when (f.extension.lowercase()) { "m4a", "aac" -> "audio/aac"; "mp3" -> "audio/mp3"; else -> "audio/ogg" }
        val prompt = "Transcribe this voice message exactly, in the language it is spoken (Bangla in Bangla script). " +
                "Then on a new line starting with SUMMARY: give a one-sentence summary in " +
                (if (bangla) "Bangla" else "English") + ". If it's silent or unclear, say so."
        val reply = GeminiProvider(key, ProviderType.GEMINI.defaultModel).chat(
            "You transcribe voice messages accurately. Never invent words.",
            listOf(ChatTurn.User(prompt, files = listOf(Attachment(f.name, mime, base64 = b64)))),
            emptyList()
        )
        return reply.text?.trim().orEmpty().ifEmpty { "(couldn't read it)" }
    }

    fun skill() = Skill(
        "voice_note", "🎙️", "Read voice messages", "\"আম্মুর ভয়েস মেসেজটা পড়ে শোনাও\" · \"শেষ ভয়েস মেসেজে কী বলছে?\"",
        "Reads WhatsApp voice messages for the user: writes out what was said and a one-line summary. who = sender " +
                "name/nickname (optional; empty = newest); count = how many (1-5). Then tell the user the summary first, " +
                "and the full words if they want.",
        listOf(
            ToolParam("who", ParamType.STRING, "who sent it (name or nickname), optional", required = false),
            ToolParam("count", ParamType.INTEGER, "how many, 1-5 (default 1)", required = false)
        )
    ) { _, args ->
        if (!FileSkills.hasAccess()) return@Skill "ERROR: Faiza needs \"All files access\" to read WhatsApp voice messages. " +
                "Ask the user to allow it (Settings → Phone access → Files)."
        val who = args.str("who")
        val notes = withContext(Dispatchers.IO) { find(who, args.int("count") ?: 1) }
        if (notes.isEmpty()) return@Skill if (who != null) "No voice message from \"$who\" found (only ones that arrived while Faiza was running can be matched to a sender)."
        else "No WhatsApp voice messages found on this phone."
        val bangla = com.faiza.ai.ai.AppLanguage.from(prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI
        val fmt = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault())
        val parts = mutableListOf<String>()
        for (n in notes) {
            val text = runCatching { withContext(Dispatchers.IO) { transcribe(n.file, bangla) } }.getOrElse { "(error: ${it.message})" }
            parts += "VOICE MESSAGE from ${n.sender ?: "unknown sender"} (${fmt.format(java.util.Date(n.at))}):\n$text"
        }
        parts.joinToString("\n\n")
    }
}
