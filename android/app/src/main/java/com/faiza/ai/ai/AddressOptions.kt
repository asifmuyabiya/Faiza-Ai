package com.faiza.ai.ai

/**
 * What she calls the user (owner's idea): "Boss", "Jaan", "Jaanu", "Sir", "Ma'am"… chosen in Settings, with the
 * list following the user's gender. Pure, so it is unit-tested.
 */
object AddressOptions {

    private val MALE = listOf("Boss", "Jaan", "Jaanu", "Sir", "Babu", "Sona", "Hero", "Sweetheart")
    private val FEMALE = listOf("Boss", "Jaan", "Jaanu", "Ma'am", "Babu", "Sona", "Princess", "Sweetheart")
    private val ANY = listOf("Boss", "Jaan", "Jaanu", "Sir", "Ma'am", "Babu", "Sona", "Sweetheart")

    fun forGender(gender: String): List<String> = when (gender) {
        "male" -> MALE
        "female" -> FEMALE
        else -> ANY
    }

    /** How the word is written in each script, so Bangla / Hindi replies don't spell it in English. */
    fun localized(address: String, base: PhraseBase): String = when (base) {
        PhraseBase.BENGALI -> when (address) {
            "Boss" -> "বস"; "Jaan" -> "জান"; "Jaanu" -> "জানু"; "Sir" -> "স্যার"; "Ma'am" -> "ম্যাম"
            "Babu" -> "বাবু"; "Sona" -> "সোনা"; "Hero" -> "হিরো"; "Princess" -> "প্রিন্সেস"; "Sweetheart" -> "সুইটহার্ট"
            else -> address
        }
        PhraseBase.HINDI -> when (address) {
            "Boss" -> "बॉस"; "Jaan" -> "जान"; "Jaanu" -> "जानू"; "Sir" -> "सर"; "Ma'am" -> "मैम"
            "Babu" -> "बाबू"; "Sona" -> "सोना"; "Hero" -> "हीरो"; "Princess" -> "प्रिंसेस"; "Sweetheart" -> "स्वीटहार्ट"
            else -> address
        }
        PhraseBase.ENGLISH -> address
    }

    /** The instruction for the system prompt (empty when nothing is set). */
    fun promptLine(userName: String?, gender: String, address: String, base: PhraseBase): String = buildString {
        if (address.isNotBlank()) {
            val word = localized(address, base)
            append("- ALWAYS call the user \"$word\" (written \"$address\" in English letters) — they chose this name for you to use. ")
            append("Use it naturally, often in place of their name, e.g. \"$word, done!\". Don't switch to another title.")
            if (!userName.isNullOrBlank()) append(" Their real name is $userName — use it only now and then.")
            appendLine()
        }
        when (gender) {
            "male" -> appendLine(
                "- The user is MALE: use masculine forms wherever the language needs them (Hindi/Urdu: \"aap kar rahe ho\", " +
                        "\"thak gaye ho\"; English: he/him when you mention them)."
            )
            "female" -> appendLine(
                "- The user is FEMALE: use feminine forms wherever the language needs them (Hindi/Urdu: \"aap kar rahi ho\", " +
                        "\"thak gayi ho\"; English: she/her when you mention them)."
            )
        }
    }.trimEnd()

    /** A fixed line that starts with "Boss," / "বস," / "बॉस," gets the user's own word instead. */
    fun personalize(line: String, address: String, base: PhraseBase): String {
        if (address.isBlank()) return line
        val word = localized(address, base)
        for (b in listOf("Boss", "বস", "बॉस")) {
            if (line.startsWith("$b,")) return word + line.substring(b.length)
        }
        return line
    }
}
