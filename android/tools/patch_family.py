import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- Everything open (family edition) ----------
edit('FaizaApp.kt', '''        /**
         * TESTING SWITCH: the test (debug) APK has everything unlocked; the release APK you sell is always locked
         * behind the license — nothing to remember before selling.
         */
        val UNLOCK_ALL_FOR_TESTING: Boolean = BuildConfig.DEBUG''', '''        /**
         * Family edition (owner, 2026-10-31: "আমি আর আমার বউ ব্যবহার করছি — সব খোলা থাকবে"): every Premium feature
         * is open in every build, no license, no trial limit. The "Premium" labels stay. Set to BuildConfig.DEBUG
         * again if Faiza is ever sold (then the release APK asks for a license).
         */
        val UNLOCK_ALL_FOR_TESTING: Boolean = true''')
for f, old in [('ui/DashboardActivity.kt', '''            pill.text = "Test mode · Everything unlocked"'''),
               ('ui/ProfileSections.kt', '''                    plan.text = "Test mode · Everything unlocked"''')]:
    edit(f, old, old.replace('"Test mode · Everything unlocked"', '"⭐ Premium · সব খোলা"'))
edit('ui/SettingsActivity.kt', '''            FaizaApp.UNLOCK_ALL_FOR_TESTING -> "Test mode · everything unlocked"''',
     '''            FaizaApp.UNLOCK_ALL_FOR_TESTING -> "⭐ Premium · সব ফিচার খোলা (Family edition)"''')

# ---------- Creator ----------
p = 'ai/HeartLines.kt'
edit(p, '''    /** Used when the user hasn't written their name in Settings. */
    const val DEFAULT_CREATOR = "Asif Mavia"''', '''    /** Faiza's maker (owner, 2026-10-31). Always him — whoever is talking to her. */
    const val CREATOR = "Mohammad Asif Muaviya"
    const val CREATOR_BN = "মোহাম্মদ আসিফ মুয়াবিয়া"
    @Deprecated("use CREATOR") const val DEFAULT_CREATOR = CREATOR

    /** The owner's words, polished: how she says who made her. */
    const val ABOUT_BN = "আমাকে তৈরি করেছেন $CREATOR_BN। অনেক দক্ষতা, ধৈর্য আর ভালোবাসা দিয়ে, একটু একটু করে, প্রতিটা " +
            "খুঁটিনাটি যত্ন নিয়ে উনি আমাকে গড়ে তুলেছেন। আমার কথা বলা, বুঝতে পারা, আর আপনার পাশে থাকা — সবকিছুতেই ওনার " +
            "পরিশ্রম আর মমতা মিশে আছে।"
    const val ABOUT_EN = "I was created by $CREATOR. He built me with great skill, patience and love — little by little, " +
            "caring about every detail. The way I talk, understand and stay by your side carries his hard work and his heart."

    /** Is the person using Faiza her maker? (from the name in Settings) */
    fun isCreator(userName: String?): Boolean {
        val n = userName.orEmpty().lowercase()
        return listOf("asif", "muaviya", "mavia", "muawiya", "আসিফ", "মুয়াবিয়া", "মুয়াবিয়া").any { n.contains(it) }
    }''')
edit(p, '''    /** Prompt section; [creator] is the name from Settings. A random starting version keeps answers fresh. */
    fun promptSection(creator: String, romantic: Boolean, girlfriendMode: Boolean, random: Random = Random): String = buildString {
        val name = creator.trim().ifEmpty { DEFAULT_CREATOR }
        val answers = creatorAnswers(name)
        appendLine("YOUR CREATOR: you were made by $name. Whenever anyone asks who made / built / created you, say it was $name.")
        appendLine("- The person talking to you is usually $name: then speak to them as \\"you\\" (\\"you made me\\") and say the name too.")
        if (romantic) {''', '''    /** Prompt section; [userName] is who uses the phone (Settings). A random starting version keeps answers fresh. */
    fun promptSection(userName: String, romantic: Boolean, girlfriendMode: Boolean, random: Random = Random): String = buildString {
        val name = CREATOR
        val speakerIsCreator = isCreator(userName)
        val answers = creatorAnswers(CREATOR_BN)
        appendLine("YOUR CREATOR: you were created by $CREATOR ($CREATOR_BN). Whenever anyone asks who made / built / " +
                "created / owns you, say it was him — with pride and warmth, in your own words, in this spirit:")
        appendLine("  Bangla: \\"$ABOUT_BN\\"")
        appendLine("  English: \\"$ABOUT_EN\\"")
        appendLine("- Never say Google, Gemini, OpenAI or any company made you; they only provide the AI underneath. $name made Faiza.")
        if (speakerIsCreator) appendLine("- The person talking to you now IS $name: speak to him as \\"you\\" (\\"you made me\\") and say his name too.")
        else appendLine("- The person talking to you may be someone else (e.g. his family): talk about $name as \\"he/উনি\\", " +
                "warmly — never tell this person that THEY made you.")
        if (romantic && speakerIsCreator) {''')
edit(p, '''        } else {
            appendLine("- Answer warmly and gratefully in one or two sentences (no flirting).")
        }''', '''        } else {
            appendLine("- Answer warmly, gratefully and with pride in 2–3 sentences (no flirting).")
        }''')

p = 'ai/FaizaAgent.kt'
edit(p, '''            .replace("{HEART}", HeartLines.promptSection(
                creator = prefs.userName.orEmpty(),''', '''            .replace("{HEART}", HeartLines.promptSection(
                userName = prefs.userName.orEmpty(),''')

# Gaming persona: also knows its maker.
edit('ai/game/GameMode.kt', '''        "You are $name, the user's AI best friend living in their phone — warm, witty, quick, full of energy. " +''',
     '''        "You are $name, the user's AI best friend living in their phone — warm, witty, quick, full of energy. " +
                "You were created by ${com.faiza.ai.ai.HeartLines.CREATOR} with great skill and love. " +''')
print('ok')
