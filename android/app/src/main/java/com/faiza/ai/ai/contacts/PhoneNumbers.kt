package com.faiza.ai.ai.contacts

/** Phone-number helpers that don't need Android (so they can be unit-tested). */
object PhoneNumbers {

    data class Country(val name: String, val iso: String, val dialCode: String)

    val COUNTRIES = listOf(
        Country("Bangladesh", "bd", "880"),
        Country("India", "in", "91"),
        Country("Pakistan", "pk", "92"),
        Country("Nepal", "np", "977"),
        Country("Sri Lanka", "lk", "94"),
        Country("UAE", "ae", "971"),
        Country("Saudi Arabia", "sa", "966"),
        Country("Qatar", "qa", "974"),
        Country("Kuwait", "kw", "965"),
        Country("Oman", "om", "968"),
        Country("Malaysia", "my", "60"),
        Country("Singapore", "sg", "65"),
        Country("USA / Canada", "us", "1"),
        Country("United Kingdom", "gb", "44")
    )

    fun dialCodeForIso(iso: String?): String? =
        COUNTRIES.firstOrNull { it.iso.equals(iso, ignoreCase = true) }?.dialCode
            ?: if (iso.equals("ca", ignoreCase = true)) "1" else null

    fun countryForDial(dial: String): Country? = COUNTRIES.firstOrNull { it.dialCode == dial }

    /**
     * Digits-only international form for WhatsApp links etc.
     * "+880 1712-345678" → "8801712345678"; "01712345678" with dial "880" → "8801712345678".
     */
    fun toInternational(number: String, dialCode: String?): String {
        val digits = number.filter { it.isDigit() }
        return when {
            number.trim().startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") && !dialCode.isNullOrEmpty() -> dialCode + digits.drop(1)
            !dialCode.isNullOrEmpty() && digits.length in 6..10 && !digits.startsWith(dialCode) -> dialCode + digits
            else -> digits
        }
    }

    /** Same phone number written differently ("+880 1712…" vs "01712…")? Compares the last 9 digits. */
    fun sameNumber(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.length < 6 || db.length < 6) return da == db && da.isNotEmpty()
        return da.takeLast(9) == db.takeLast(9)
    }
}
