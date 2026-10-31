package com.faiza.ai.ai.guardian

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.util.ActivityLog

/**
 * Delete guard (owner's idea): when any app asks "Delete…?" / "Uninstall?" / "Clear data?", Faiza speaks up before
 * the user confirms — a gentle check for normal deletes, a strong warning when a lot could be lost, and a
 * reassurance for duplicates. Android doesn't let apps block another app's button, so she warns, never blocks.
 */
object DeleteGuard {

    enum class Level { NONE, NORMAL, IMPORTANT, IMPORTANT_FILE, DUPLICATE }

    private val DELETE_WORDS = listOf(
        "delete", "remove", "erase", "uninstall", "clear data", "clear storage", "clear all", "format", "factory reset",
        "move to trash", "move to bin", "trash", "discard", "wipe",
        "মুছ", "ডিলিট", "ডিলেট", "আনইনস্টল", "সরান", "সরিয়ে", "হটা", "हटा", "डिलीट", "मिटा", "अनइंस्टॉल"
    )
    private val CANCEL_WORDS = listOf("cancel", "no", "not now", "keep", "বাতিল", "না", "রাখুন", "रद्द", "नहीं")
    private val DUPLICATE_WORDS = listOf("duplicate", "ডুপ্লিকেট", "ডুপ্লিকেট", "डुप्लिकेट", "copies of", "same file")
    private val IMPORTANT_WORDS = listOf(
        "permanent", "can't be undone", "cannot be undone", "can not be undone", "won't be able to", "all ", "everything",
        "factory", "format", "clear data", "clear storage", "all data", "account", "backup", "chat", "conversation",
        "uninstall", "folder", "album",
        "স্থায়ী", "স্থায়ী", "সব ", "সমস্ত", "ফেরত আনা যাবে না", "ফিরিয়ে আনা যাবে না", "অ্যাকাউন্ট", "চ্যাট", "ফোল্ডার",
        "स्थायी", "सभी", "सब ", "वापस नहीं"
    )

    /** Names that usually mean an important document (ID, certificates, money, work). */
    private val IMPORTANT_FILE_WORDS = listOf(
        "passport", "nid", "national id", "birth certificate", "certificate", "marksheet", "transcript", "cv", "resume",
        "contract", "agreement", "deed", "invoice", "receipt", "bank", "statement", "salary", "tax", "license", "licence",
        "visa", "policy", "insurance", "prescription", "medical report", "backup", "wallet", "seed",
        ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".apk", ".key", ".db",
        "পাসপোর্ট", "আইডি", "জাতীয় পরিচয়", "সার্টিফিকেট", "সনদ", "দলিল", "চুক্তি", "রসিদ", "ব্যাংক", "প্রেসক্রিপশন",
        "पासपोर्ट", "प्रमाणपत्र", "आधार", "बैंक"
    )

    /** Pure: is this a delete confirmation, and how serious? [buttons] = the short clickable texts. */
    fun analyze(body: List<String>, buttons: List<String>): Level {
        val btn = buttons.map { it.lowercase().trim() }
        val all = (body + buttons).joinToString(" \n ").lowercase()
        val deleteButton = btn.any { b -> b.length <= 30 && DELETE_WORDS.any { b.contains(it) } }
        val cancelButton = btn.any { b -> CANCEL_WORDS.any { b == it } }
        if (!deleteButton || !(cancelButton || all.contains('?'))) return Level.NONE
        if (DUPLICATE_WORDS.any { all.contains(it) }) return Level.DUPLICATE
        if (IMPORTANT_FILE_WORDS.any { w ->
                if (w.startsWith(".")) all.contains(w)
                else Regex("(^|[^\\p{L}\\p{M}])" + Regex.escape(w) + "([^\\p{L}\\p{M}]|$)").containsMatchIn(all)
            })
            return Level.IMPORTANT_FILE
        val bigCount = Regex("\\d+").findAll(all).mapNotNull { it.value.toIntOrNull() }.any { it in 10..100_000 }
        return if (bigCount || IMPORTANT_WORDS.any { all.contains(it) }) Level.IMPORTANT else Level.NORMAL
    }

    fun line(level: Level, b: PhraseBase): String = when (level) {
        Level.IMPORTANT_FILE -> when (b) {
            PhraseBase.BENGALI -> "বস, এই ফাইলটা তো অনেক গুরুত্বপূর্ণ মনে হচ্ছে! ডিলিট করার আগে আরেকবার ভালো করে দেখে নিন — দরকার হলে আগে একটা কপি রেখে দিন।"
            PhraseBase.HINDI -> "बॉस, ये फ़ाइल बहुत ज़रूरी लग रही है! डिलीट करने से पहले एक बार अच्छे से देख लीजिए।"
            PhraseBase.ENGLISH -> "Boss, this file looks important! Take one more careful look before deleting — keep a copy if you need it."
        }
        Level.IMPORTANT -> when (b) {
            PhraseBase.BENGALI -> "বস, সাবধান! এটা ডিলিট করলে অনেক ডেটা হারিয়ে যেতে পারে, অনেক সমস্যা হতে পারে। প্লিজ আরেকবার চেক করে নিন, তারপর ডিলিট করুন।"
            PhraseBase.HINDI -> "बॉस, सावधान! इसे डिलीट करने से बहुत सारा डेटा खो सकता है। प्लीज़ एक बार फिर चेक कर लीजिए।"
            PhraseBase.ENGLISH -> "Boss, careful! Deleting this could lose a lot of data. Please check once more before you delete."
        }
        Level.DUPLICATE -> when (b) {
            PhraseBase.BENGALI -> "বস, এটা ডুপ্লিকেট মনে হচ্ছে — ডিলিট করলে সমস্যা নেই।"
            PhraseBase.HINDI -> "बॉस, ये डुप्लिकेट लग रहा है — डिलीट करने में कोई दिक्कत नहीं।"
            PhraseBase.ENGLISH -> "Boss, this looks like a duplicate — it's fine to delete."
        }
        else -> when (b) {
            PhraseBase.BENGALI -> "বস, আপনি কি সত্যিই এটা ডিলিট করতে চান? নিশ্চিত হয়ে তারপর ডিলিট করুন।"
            PhraseBase.HINDI -> "बॉस, क्या आप सच में इसे डिलीट करना चाहते हैं? पक्का करके ही डिलीट कीजिए।"
            PhraseBase.ENGLISH -> "Boss, do you really want to delete this? Make sure, then delete."
        }
    }

    // ===== On the phone =====

    private var lastKey = 0
    private var lastAt = 0L

    /** A window just opened: is it a delete confirmation? (called from the accessibility service) */
    fun check(svc: FaizaAccessibilityService) {
        val prefs = FaizaApp.instance.prefs
        if (!prefs.isDeleteGuard) return
        // Faiza herself is deleting / clicking (a tool is running): don't warn about her own actions.
        if (SystemClock.elapsedRealtime() < FaizaAccessibilityService.automationUntil) return
        if (com.faiza.ai.ai.skills.TaskPlan.running) return
        val root = svc.rootInActiveWindow ?: return
        if (root.packageName?.toString() == svc.packageName) return
        val body = mutableListOf<String>()
        val buttons = mutableListOf<String>()
        var count = 0
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 25 || count > 120 || !n.isVisibleToUser) return
            count++
            val t = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) {
                val cls = n.className?.toString().orEmpty()
                if ((n.isClickable || cls.contains("Button")) && t.length <= 30) buttons += t else body += t
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        if (count > 120) return // a whole screen, not a confirmation box
        val level = analyze(body, buttons)
        if (level == Level.NONE) return
        val key = (root.packageName.toString() + body.joinToString()).hashCode()
        val now = SystemClock.elapsedRealtime()
        if ((key == lastKey && now - lastAt < 30_000) || now - lastAt < 6_000) return
        lastKey = key
        lastAt = now

        val text = com.faiza.ai.ai.AddressOptions.personalize(line(level, OwnerGuard.base), prefs.userAddress, OwnerGuard.base)
        ActivityLog.add("security", "Delete check", text)
        val spoke = FloatingOrbService.instance?.warnUser(text) == true
        if (!spoke) {
            val n = NotificationCompat.Builder(svc, FaizaApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_delete)
                .setContentTitle(if (level == Level.IMPORTANT) "⚠️ Careful before deleting" else "Delete check")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(svc).notify(7801, n) }
        }
    }
}
