package com.faiza.ai.ai.memory

/**
 * Incognito guard: spots passwords, OTPs, PINs, CVVs and card/account numbers
 * (English, Bengali, Hindi) so they are never written to memory.
 */
object SensitiveFilter {

    /** Words that make a sentence secret on their own. */
    private val SECRET_WORDS = Regex(
        "(?i)\\b(password|passcode|passwd|otp|one[- ]time password|cvv|cvc|pin code|pin number|" +
                "card number|account number|security code|verification code|bkash pin|nagad pin)\\b|" +
                "পাসওয়ার্ড|পাসওয়ার্ড|ওটিপি|পিন কোড|পিন নম্বর|কার্ড নম্বর|কার্ড নাম্বার|অ্যাকাউন্ট নম্বর|অ্যাকাউন্ট নাম্বার|ভেরিফিকেশন কোড|" +
                "पासवर्ड|ओटीपी|पिन कोड|कार्ड नंबर|अकाउंट नंबर|खाता संख्या"
    )

    /** "code"/"pin" only count as secret with a number ("my pin is 4821"), not "pin this chat". */
    private val CODE_WORD = Regex("(?i)\\b(code|pin)\\b|কোড|পিন|कोड|पिन")
    private val DIGIT_RUN = Regex("\\d[\\d -]{2,}\\d")

    /** keyword + optional "is/:" + the secret itself. */
    private val SECRET_VALUE = Regex(
        "(?i)(password|passcode|otp|cvv|cvc|pin|code|পাসওয়ার্ড|পাসওয়ার্ড|ওটিপি|পিন|কোড|पासवर्ड|ओटीपी|पिन|कोड)" +
                "(\\s*(is|was|=|:|হল|হলো|হচ্ছে|है|था)?\\s*)(\\S+)"
    )

    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        if (SECRET_WORDS.containsMatchIn(text)) return true
        if (CODE_WORD.containsMatchIn(text) && DIGIT_RUN.containsMatchIn(text)) return true
        return containsCardNumber(text)
    }

    /** Hides the secret parts of a sentence but keeps the rest readable. */
    fun redact(text: String): String {
        var out = SECRET_VALUE.replace(text) { m -> m.groupValues[1] + m.groupValues[2] + "[hidden]" }
        out = DIGIT_RUN.replace(out) { m ->
            val digits = m.value.filter { it.isDigit() }
            if (luhnValid(digits) && digits.length in 13..19) "[hidden]" else m.value
        }
        if (isSensitive(text)) {
            // Any remaining 4+ digit run next to a secret word is hidden too.
            out = Regex("\\d{4,}").replace(out, "[hidden]")
        }
        return out
    }

    private fun containsCardNumber(text: String): Boolean =
        DIGIT_RUN.findAll(text).any { m ->
            val digits = m.value.filter { it.isDigit() }
            digits.length in 13..19 && luhnValid(digits)
        }

    private fun luhnValid(digits: String): Boolean {
        if (digits.length < 13) return false
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}
