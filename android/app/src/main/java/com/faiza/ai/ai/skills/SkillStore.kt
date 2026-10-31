package com.faiza.ai.ai.skills

import com.faiza.ai.ai.provider.AIProviders
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** A ready-made routine from the Skill store (MAYA "Skill store"). Installing it adds a custom skill. */
data class StoreSkill(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val trigger: String,
    val instructions: String
)

object SkillStore {

    /**
     * Optional online catalog (a JSON list of StoreSkill) so new skills can be published without an app update,
     * e.g. a raw GitHub file. Empty = only the built-in catalog below.
     */
    const val REMOTE_URL = ""

    val BUILT_IN = listOf(
        StoreSkill(
            "daily-briefing", "☀️", "Daily briefing", "Good-morning update: date, weather, reminders, news",
            "good morning / দিনের আপডেট দাও / din ka update do",
            "Greet the user warmly by name. Tell today's day and date. Call get_weather and give today's weather in one " +
                    "sentence. Call list_routines and mention today's reminders. Call read_notifications and summarise " +
                    "anything important in one sentence. Call search_web for one top news headline in the user's " +
                    "country and say it briefly. End with one short encouraging line. Keep it under 30 seconds."
        ),
        StoreSkill(
            "whatsapp-pro", "🟢", "WhatsApp pro", "Read, reply and send files on WhatsApp — carefully",
            "whatsapp / হোয়াটসঅ্যাপে / reply on whatsapp",
            "For WhatsApp jobs: open the chat with open_whatsapp_chat (pass alternate spellings). To read new messages, " +
                    "call read_screen and read the last few messages aloud. Before sending anything, read back the " +
                    "recipient and exact text and wait for a yes. Send text with whatsapp_message (confirmed=true), " +
                    "files with share_file. In groups, say who wrote each message."
        ),
        StoreSkill(
            "call-secretary", "📞", "Call secretary", "Handles incoming calls politely for you",
            "who is calling / কে ফোন করছে / call handle karo",
            "When a call is ringing: tell the user who is calling and ask \"answer or reject?\". Answer → manage_call " +
                    "answer. Reject → manage_call reject, then offer to text the caller \"I'll call you back soon\" with " +
                    "send_sms (confirm first). For missed calls, offer to call back with call_contact."
        ),
        StoreSkill(
            "photo-share", "🖼️", "Photo share", "Send your latest photo to anyone on WhatsApp",
            "send my last photo / শেষ ছবিটা পাঠাও / last photo bhejo",
            "Call find_files with kind=image and folder=pictures (no query) to get the newest photos. Tell the user the " +
                    "newest one (name and time) and confirm the person, then call share_file with number 1, the contact " +
                    "and confirmed=true after they say yes."
        ),
        StoreSkill(
            "music-dj", "🎧", "Music DJ", "Music that fits your mood and the time of day",
            "play something / মুড অনুযায়ী গান / mood ke hisaab se gaana",
            "Pick music that fits the user's mood (from set_mood or what they say) and the time of day: calm in the " +
                    "night, upbeat in the morning, romantic in the evening. Suggest one song or playlist in one sentence, " +
                    "call play_music with it, and set_volume to a comfortable level (about 40% at night)."
        ),
        StoreSkill(
            "phone-care", "🧹", "Phone care", "A quick check-up: battery, downloads, notifications",
            "phone check up / ফোন চেক করো / phone check karo",
            "Do a quick phone check-up: battery_status (suggest charging below 20%), offer to organize the Downloads " +
                    "folder with organize_folder (show the plan first), summarise unread notifications with " +
                    "read_notifications, and at night suggest Do Not Disturb with phone_settings."
        ),
        StoreSkill(
            "study-buddy", "📚", "Study buddy", "Explains, quizzes you and saves notes",
            "help me study / পড়াতে সাহায্য করো / padhai karao",
            "Be a patient tutor. Ask the subject and topic. Explain step by step with a small everyday example, then ask " +
                    "one quick question to check. At the end offer to save short notes with create_file (format md)."
        ),
        StoreSkill(
            "bangla-news", "📰", "আজকের খবর", "Top headlines in Bangla",
            "আজকের খবর / news bolo / today's news",
            "Call search_web for today's top news in Bangladesh (search in Bengali). Read the 5 most important " +
                    "headlines, one short sentence each, and mention the source. Offer to read more about any of them " +
                    "with read_webpage."
        ),
        StoreSkill(
            "prayer-times", "🕌", "Prayer times", "Today's namaz times for your city",
            "namaz er time / নামাজের সময় / prayer times",
            "Find today's prayer times for the user's city (ask the city if you don't know it): call search_web for " +
                    "\"prayer times <city> today\" and read Fajr, Dhuhr, Asr, Maghrib and Isha. Offer to set a reminder " +
                    "for the next prayer with schedule_task."
        ),
        StoreSkill(
            "expense-tracker", "💰", "Expense tracker", "Remembers what you spend and adds it up",
            "I spent / খরচ লিখে রাখো / kharcha likho",
            "When the user tells you an expense, call remember with \"Expense <date>: <amount> <currency> for <what>\". " +
                    "When they ask how much they spent (today, this week, this month), call recall with \"Expense\" and " +
                    "add up the matching amounts. Offer a CSV with create_file if they want the list."
        )
    )

    private val gson = Gson()

    /** Built-in skills plus any from the online catalog (if set and reachable). */
    suspend fun catalog(): List<StoreSkill> {
        if (REMOTE_URL.isBlank()) return BUILT_IN
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                AIProviders.http.newCall(Request.Builder().url(REMOTE_URL).get().build()).execute().use { r ->
                    if (!r.isSuccessful) null
                    else gson.fromJson<List<StoreSkill>>(r.body?.string().orEmpty(), object : TypeToken<List<StoreSkill>>() {}.type)
                }
            }.getOrNull().orEmpty()
        }
        @Suppress("SENSELESS_COMPARISON")
        val valid = remote.filter { it != null && it.id != null && it.name != null && it.instructions != null && it.trigger != null }
        return BUILT_IN + valid.filter { r -> BUILT_IN.none { it.id == r.id } }
    }

    fun isInstalled(installed: List<CustomSkill>, s: StoreSkill) = installed.any { it.name.equals(s.name, ignoreCase = true) }

    fun install(s: StoreSkill): CustomSkill = CustomSkill(name = s.name, trigger = s.trigger, instructions = s.instructions)
}
