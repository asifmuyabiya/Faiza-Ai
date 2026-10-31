import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


# ---- Voice characters (as Google describes them; MAYA shows them in the voice list) ----
patch(J + "ai/Persona.kt", [
 ("""val FEMALE_VOICES = listOf(""", """/** What each Gemini voice sounds like. */
val VOICE_TRAITS = mapOf(
    "Zephyr" to "Bright", "Puck" to "Upbeat", "Charon" to "Informative", "Kore" to "Firm", "Fenrir" to "Excitable",
    "Leda" to "Youthful", "Orus" to "Firm", "Aoede" to "Breezy", "Callirrhoe" to "Easy-going", "Autonoe" to "Bright",
    "Enceladus" to "Breathy", "Iapetus" to "Clear", "Umbriel" to "Easy-going", "Algieba" to "Smooth",
    "Despina" to "Smooth", "Erinome" to "Clear", "Algenib" to "Gravelly", "Rasalgethi" to "Informative",
    "Laomedeia" to "Upbeat", "Achernar" to "Soft", "Alnilam" to "Firm", "Schedar" to "Even", "Gacrux" to "Mature",
    "Pulcherrima" to "Forward", "Achird" to "Friendly", "Zubenelgenubi" to "Casual", "Vindemiatrix" to "Gentle",
    "Sadachbia" to "Lively", "Sadaltager" to "Knowledgeable", "Sulafat" to "Warm"
)

/** "Breezy · Aoede" */
fun voiceLabel(voice: String): String = VOICE_TRAITS[voice]?.let { "$it · $voice" } ?: voice

val FEMALE_VOICES = listOf("""),
])

patch(J + "ui/SettingsActivity.kt", [
 ("""                .setSingleChoiceItems(voices.toTypedArray(), picked) { _, which ->""",
  """                .setSingleChoiceItems(voices.map { com.faiza.ai.ai.voiceLabel(it) }.toTypedArray(), picked) { _, which ->"""),
 ("""            "${prefs.liveVoiceFor(persona.id, persona.liveVoice)} · for ${persona.displayName}\"""",
  """            "${com.faiza.ai.ai.voiceLabel(prefs.liveVoiceFor(persona.id, persona.liveVoice))} · for ${persona.displayName}\""""),
])

# ---- Macro step badge ("7/14") while a macro runs ----
patch(J + "ai/macros/MacroRunner.kt", [
 ("""    @Volatile var runningName: String? = null
        private set""", """    @Volatile var runningName: String? = null
        private set

    /** (macro name, current step, total) while a macro runs — shown as a "3/7" badge on screen (MAYA). */
    private val _progress = kotlinx.coroutines.flow.MutableStateFlow<Triple<String, Int, Int>?>(null)
    val progress: kotlinx.coroutines.flow.StateFlow<Triple<String, Int, Int>?> get() = _progress"""),
 ("""            runningName = null""", """            runningName = null
            _progress.value = null"""),
 ("""        for ((i, step) in macro.steps.withIndex()) {""", """        for ((i, step) in macro.steps.withIndex()) {
            _progress.value = Triple(macro.name, i + 1, macro.steps.size)"""),
])

patch(J + "service/FloatingOrbService.kt", [
 ("""        scope.launch { MacroCenter.recorder.state.collect { showRecordingPill(it) } }""",
  """        scope.launch { MacroCenter.recorder.state.collect { showRecordingPill(it) } }
        scope.launch { com.faiza.ai.ai.macros.MacroRunner.progress.collect { showMacroPill(it) } }"""),
 ("""    // ========== EDGE GLOW (video 27:13) ==========""", """    private var macroPill: TextView? = null

    /** "▶ Notes · 3/7" while a macro is playing. */
    private fun showMacroPill(p: Triple<String, Int, Int>?) {
        if (p == null) {
            macroPill?.let { runCatching { windowManager.removeView(it) } }
            macroPill = null
            return
        }
        val density = resources.displayMetrics.density
        val pill = macroPill ?: TextView(this).apply {
            setBackgroundResource(R.drawable.bg_macro_pill)
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
        }.also { v ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (84 * density).toInt()
            }
            if (runCatching { windowManager.addView(v, lp) }.isFailure) return
            macroPill = v
        }
        pill.text = "▶  ${p.first} · ${p.second}/${p.third}"
    }

    // ========== EDGE GLOW (video 27:13) =========="""),
 ("""        showRecordingPill(null)""", """        showRecordingPill(null)
        showMacroPill(null)"""),
])

open("res/drawable/bg_macro_pill.xml", "w", encoding="utf-8").write("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#E6111118" />
    <stroke android:width="1dp" android:color="#668B5CF6" />
    <corners android:radius="999dp" />
</shape>
""")

# ---- Splash (MAYA: "One moment… loading 💗") ----
patch("res/layout/activity_main.xml", [
 ("""            android:text="Your phone, by voice" />""", """            android:text="One moment… loading 💜" />"""),
])
print("voices + macro badge + splash done")
