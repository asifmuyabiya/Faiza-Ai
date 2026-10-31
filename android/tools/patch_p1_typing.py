import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


def nav_row(key, icon, color, title, sub):
    return f"""
                <LinearLayout
                    android:id="@+id/row{key}"
                    style="@style/Widget.Faiza.Row">

                    <ImageView
                        style="@style/Widget.Faiza.IconTile"
                        android:background="@drawable/bg_tile_{color}"
                        android:src="@drawable/{icon}"
                        app:tint="@color/tile_{color}" />

                    <LinearLayout style="@style/Widget.Faiza.RowText">

                        <TextView
                            style="@style/Widget.Faiza.RowTitle"
                            android:text="{title}" />

                        <TextView
                            android:id="@+id/sub{key}"
                            style="@style/Widget.Faiza.RowSubtitle"
                            android:text="{sub}" />
                    </LinearLayout>

                    <ImageView style="@style/Widget.Faiza.Chevron" />
                </LinearLayout>

                <View style="@style/Widget.Faiza.Divider" />
"""


patch("res/layout/activity_orb_settings.xml", [
 ("""                        android:id="@+id/switchRealisticTyping"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>

                <View style="@style/Widget.Faiza.Divider" />
""", """                        android:id="@+id/switchRealisticTyping"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>

                <View style="@style/Widget.Faiza.Divider" />
""" + nav_row("TypingSpeed", "ic_timer", "cyan", "Typing speed", "Normal")
    + nav_row("TypingApps", "ic_smartphone", "slate", "Where to type like a person", "Every app")),
])

patch(J + "util/PrefsManager.kt", [
 ("""    var isRealisticTyping: Boolean""", """    /** "slow", "normal" or "fast" (MAYA Realistic typing speed). */
    var typingSpeed: String
        get() = prefs.getString("typing_speed", "normal") ?: "normal"
        set(value) = prefs.edit().putString("typing_speed", value).apply()

    /** Apps (package names, comma-separated) where she types letter by letter; empty = every app. */
    var typingApps: String
        get() = prefs.getString("typing_apps", "") ?: ""
        set(value) = prefs.edit().putString("typing_apps", value).apply()

    var isRealisticTyping: Boolean"""),
])

patch(J + "service/FaizaAccessibilityService.kt", [
 ("""        val perChar = (12_000L / points.size).coerceIn(12L, 85L)""",
  """        // Slow / Normal / Fast (MAYA). Long texts speed up so typing never takes forever.
        val (base, maxTotal) = when (com.faiza.ai.FaizaApp.instance.prefs.typingSpeed) {
            "slow" -> 140L to 25_000L
            "fast" -> 30L to 8_000L
            else -> 75L to 14_000L
        }
        val perChar = minOf(base, maxTotal / points.size).coerceAtLeast(10L)"""),
 ("""    /** Types all of [text] at once. */""", """    /** Realistic typing is on, and this app is one of the chosen ones (or none are chosen). */
    fun typeLikePerson(): Boolean {
        val prefs = com.faiza.ai.FaizaApp.instance.prefs
        if (!prefs.isRealisticTyping) return false
        val apps = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return apps.isEmpty() || foregroundPackage in apps
    }

    /** Types all of [text] at once. */"""),
])

for p in [J + "ai/macros/MacroRunner.kt", J + "ai/skills/BuiltInSkills.kt"]:
    patch(p, [("if (FaizaApp.instance.prefs.isRealisticTyping) a.typeTextHuman(text) else a.typeText(text)",
               "if (a.typeLikePerson()) a.typeTextHuman(text) else a.typeText(text)")])

patch(J + "ui/OrbSettingsActivity.kt", [
 ("""        bind(R.id.switchRealisticTyping, { prefs.isRealisticTyping }) { prefs.isRealisticTyping = it }""",
  """        bind(R.id.switchRealisticTyping, { prefs.isRealisticTyping }) { prefs.isRealisticTyping = it }
        setupTypingOptions()"""),
])

s = open(J + "ui/OrbSettingsActivity.kt", encoding="utf-8").read()
idx = s.rstrip().rfind("}")
s = s[:idx] + '''
    // ===== Realistic typing: speed and apps (MAYA) =====

    private fun setupTypingOptions() {
        val speeds = listOf("slow" to "Slow", "normal" to "Normal", "fast" to "Fast")
        val apps = listOf(
            "com.google.android.keep" to "Google Keep", "com.google.android.apps.docs.editors.docs" to "Google Docs",
            "com.samsung.android.app.notes" to "Samsung Notes", "com.whatsapp" to "WhatsApp",
            "com.facebook.orca" to "Messenger", "com.google.android.gm" to "Gmail",
            "com.google.android.apps.messaging" to "Messages", "com.openai.chatgpt" to "ChatGPT",
            "com.google.android.apps.bard" to "Gemini", "com.termux" to "Termux"
        )
        fun refresh() {
            findViewById<android.widget.TextView>(R.id.subTypingSpeed).text =
                speeds.firstOrNull { it.first == prefs.typingSpeed }?.second ?: "Normal"
            val chosen = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            findViewById<android.widget.TextView>(R.id.subTypingApps).text = when {
                chosen.isEmpty() -> "Every app"
                else -> chosen.joinToString { p -> apps.firstOrNull { it.first == p }?.second ?: p.substringAfterLast('.') }
            }
        }
        findViewById<android.view.View>(R.id.rowTypingSpeed).setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Typing speed")
                .setSingleChoiceItems(speeds.map { it.second }.toTypedArray(),
                    speeds.indexOfFirst { it.first == prefs.typingSpeed }.coerceAtLeast(1)) { d, which ->
                    prefs.typingSpeed = speeds[which].first
                    refresh()
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<android.view.View>(R.id.rowTypingApps).setOnClickListener {
            val chosen = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            val checked = apps.map { it.first in chosen }.toBooleanArray()
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Type like a person in…")
                .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, on ->
                    if (on) chosen += apps[which].first else chosen -= apps[which].first
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.typingApps = chosen.joinToString(",")
                    refresh()
                }
                .setNeutralButton("Every app") { _, _ ->
                    prefs.typingApps = ""
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refresh()
    }
}
'''
open(J + "ui/OrbSettingsActivity.kt", "w", encoding="utf-8").write(s)
print("typing done")
