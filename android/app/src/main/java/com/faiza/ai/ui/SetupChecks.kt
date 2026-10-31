package com.faiza.ai.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.faiza.ai.FaizaApp
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FaizaNotificationListener

/** Everything Faiza needs switched on, how to check it and how to fix it (used by the setup wizard and the guide). */
object SetupChecks {

    data class Check(
        val id: String,
        val emoji: String,
        val title: String,
        val why: String,
        /** What to do on the next screen, in plain words. */
        val how: String,
        val essential: Boolean,
        val ok: (Activity) -> Boolean,
        val fix: (Activity) -> Unit
    )

    private fun perm(a: Activity, p: String) = a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun open(a: Activity, i: Intent) { runCatching { a.startActivity(i) } }
    private fun appSettings(a: Activity) = open(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
    private fun ask(a: Activity, vararg p: String) {
        // Asked before and refused for good → only the app settings page can change it.
        if (p.any { !a.shouldShowRequestPermissionRationale(it) } && FaizaApp.instance.prefs.askedPermissions.containsAll(p.toList())) appSettings(a)
        else a.requestPermissions(arrayOf(*p), 77)
        FaizaApp.instance.prefs.askedPermissions = FaizaApp.instance.prefs.askedPermissions + p
    }

    val all: List<Check> = listOf(
        Check("key", "🧠", "AI key (Gemini)", "ফাইজার মাথা — এটা ছাড়া কিছুই চলবে না",
            "পরের পাতায় ফ্রি Gemini key বসান (কীভাবে নেবেন সেটাও দেখানো আছে)।", true,
            { FaizaApp.instance.prefs.isAiConfigured() }, { open(it, Intent(it, ApiSetupActivity::class.java)) }),
        Check("mic", "🎙️", "মাইক্রোফোন", "আপনার কথা শোনার জন্য",
            "\"Allow\" / \"While using the app\" চাপুন।", true,
            { perm(it, Manifest.permission.RECORD_AUDIO) }, { ask(it, Manifest.permission.RECORD_AUDIO) }),
        Check("overlay", "🫧", "অন্য অ্যাপের উপরে দেখানো", "ভাসমান বাবল আর মিনি জানালার জন্য",
            "লিস্টে Faiza AI খুঁজে সুইচটা চালু করুন, তারপর ফিরে আসুন।", true,
            { Settings.canDrawOverlays(it) }, { open(it, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}"))) }),
        Check("notify", "🔔", "নোটিফিকেশন দেখানো", "রিমাইন্ডার, অ্যালার্ম আর ফাইজার বার্তার জন্য",
            "\"Allow\" চাপুন।", true,
            { Build.VERSION.SDK_INT < 33 || perm(it, Manifest.permission.POST_NOTIFICATIONS) },
            { if (Build.VERSION.SDK_INT >= 33) ask(it, Manifest.permission.POST_NOTIFICATIONS) }),
        Check("battery", "🔋", "ব্যাটারি সেভার থেকে বাদ", "যাতে ফোন ফাইজাকে ঘুম পাড়িয়ে না দেয়",
            "\"Allow\" চাপুন। (Honor/Huawei: Battery → App launch → Faiza → Manage manually, সব চালু)", true,
            { it.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(it.packageName) == true },
            { open(it, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}"))) }),
        Check("a11y", "♿", "Accessibility", "ফোন চালানো, স্ক্রিন দেখা, গেমিং মোড, ইয়ারফোন বাটন",
            "\"Installed apps\" / \"Downloaded apps\" → Faiza AI → চালু করুন → Allow।", false,
            { FaizaAccessibilityService.instance != null }, { open(it, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }),
        Check("listener", "📨", "নোটিফিকেশন পড়া", "কে কল/মেসেজ দিল বলা, ভয়েস মেসেজ",
            "লিস্টে Faiza AI চালু করুন → Allow।", false,
            { FaizaNotificationListener.instance != null }, { open(it, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }),
        Check("phone", "📞", "ফোন ও কল", "কল এলে নাম বলা, ধরা/কাটা, কল দেওয়া",
            "\"Allow\" চাপুন।", false,
            { perm(it, Manifest.permission.READ_PHONE_STATE) && perm(it, Manifest.permission.CALL_PHONE) },
            { ask(it, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CONTACTS) }),
        Check("sms", "💬", "SMS", "\"পরে কল দিচ্ছি\" মেসেজ, bKash হিসাব",
            "\"Allow\" চাপুন।", false,
            { perm(it, Manifest.permission.SEND_SMS) }, { ask(it, Manifest.permission.SEND_SMS) }),
        Check("camera", "📷", "ক্যামেরা", "ছবি তোলা, পড়া বোঝানো, প্রেসক্রিপশন",
            "\"Allow\" চাপুন।", false,
            { perm(it, Manifest.permission.CAMERA) }, { ask(it, Manifest.permission.CAMERA) }),
        Check("location", "📍", "লোকেশন", "নামাজের সময়, কিবলা, গাড়ির গতি, SOS",
            "\"While using the app\" চাপুন।", false,
            { perm(it, Manifest.permission.ACCESS_FINE_LOCATION) }, { ask(it, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) }),
        Check("files", "📁", "সব ফাইল দেখা", "ফাইল ম্যানেজার, WhatsApp ভয়েস মেসেজ, Code Studio",
            "Faiza AI-এর সুইচ চালু করুন।", false,
            { com.faiza.ai.ai.files.FileSkills.hasAccess() },
            { if (Build.VERSION.SDK_INT >= 30) open(it, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${it.packageName}"))) else appSettings(it) })
    )
}
