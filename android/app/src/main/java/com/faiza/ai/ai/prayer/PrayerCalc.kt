package com.faiza.ai.ai.prayer

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer times worked out on the phone (no internet), the standard astronomical method (as PrayTimes.org).
 * Default = Bangladesh: University of Islamic Sciences, Karachi (Fajr 18°, Isha 18°), Hanafi Asr.
 * Also the Hijri date (tabular calendar, ± a day vs. local moon sighting) and the Qibla direction.
 */
object PrayerCalc {

    /** Minutes after local midnight; -1 if it can't be worked out (polar days). */
    data class Times(val fajr: Int, val sunrise: Int, val dhuhr: Int, val asr: Int, val maghrib: Int, val isha: Int) {
        fun list(): List<Pair<String, Int>> = listOf(
            "Fajr" to fajr, "Sunrise" to sunrise, "Dhuhr" to dhuhr, "Asr" to asr, "Maghrib" to maghrib, "Isha" to isha
        )
        /** The five prayers only. */
        fun prayers(): List<Pair<String, Int>> = list().filter { it.first != "Sunrise" }
    }

    enum class Method(val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
        KARACHI("Karachi (Bangladesh, Pakistan, India)", 18.0, 18.0),
        MWL("Muslim World League", 18.0, 17.0),
        UMM_AL_QURA("Umm al-Qura (Saudi Arabia)", 18.5, 0.0, 90),
        EGYPT("Egyptian", 19.5, 17.5),
        ISNA("ISNA (North America)", 15.0, 15.0),
        DUBAI("Dubai (UAE)", 18.2, 18.2);

        companion object { fun from(s: String?) = values().firstOrNull { it.name.equals(s, true) } ?: KARACHI }
    }

    private fun dtr(d: Double) = d * Math.PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / Math.PI
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)

    /** Julian day number at 0h UT of a Gregorian date. */
    fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private class Sun(val decl: Double, val eqt: Double)

    private fun sun(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(dtr(g)) + 0.020 * sin(dtr(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = rtd(atan2(cos(dtr(e)) * sin(dtr(l)), cos(dtr(l)))) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = rtd(asin(sin(dtr(e)) * sin(dtr(l))))
        return Sun(decl, eqt)
    }

    /**
     * Times for one day. [tz] = hours from UTC (Bangladesh +6). [asrFactor] 2 = Hanafi (Bangladesh), 1 = Shafi'i.
     */
    fun times(year: Int, month: Int, day: Int, lat: Double, lng: Double, tz: Double,
              method: Method = Method.KARACHI, asrFactor: Int = 2): Times {
        val jDate = julian(year, month, day) - lng / (15.0 * 24.0)

        fun midDay(t: Double): Double = fixHour(12.0 - sun(jDate + t).eqt)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jDate + t).decl
            val noon = midDay(t)
            val c = (-sin(dtr(angle)) - sin(dtr(decl)) * sin(dtr(lat))) / (cos(dtr(decl)) * cos(dtr(lat)))
            if (c < -1 || c > 1) return Double.NaN
            val tt = rtd(acos(c)) / 15.0
            return noon + if (ccw) -tt else tt
        }
        fun asr(t: Double): Double {
            val decl = sun(jDate + t).decl
            val angle = -rtd(atan(1.0 / (asrFactor + tan(dtr(abs(lat - decl))))))
            return angleTime(angle, t, false)
        }

        // Two passes: the second uses the first pass's times for the sun's position (more exact).
        var fajr = 5.0; var rise = 6.0; var dhuhr = 12.0; var asrT = 13.0; var set = 18.0; var isha = 18.0
        repeat(2) {
            fajr = angleTime(method.fajr, fajr / 24.0, true)
            rise = angleTime(0.833, rise / 24.0, true)
            dhuhr = midDay(dhuhr / 24.0)
            asrT = asr(asrT / 24.0)
            set = angleTime(0.833, set / 24.0, false)
            isha = if (method.ishaMinutes > 0) set + method.ishaMinutes / 60.0 else angleTime(method.isha, isha / 24.0, false)
            if (fajr.isNaN()) fajr = 5.0
            if (isha.isNaN()) isha = 18.0
        }
        val shift = tz - lng / 15.0
        fun minutes(h: Double, plusMin: Int = 0): Int =
            if (h.isNaN()) -1 else ((fixHour(h + shift) * 60.0) + 0.5).toInt().plus(plusMin).mod(24 * 60)
        // Dhuhr a minute after true noon, Maghrib a minute after sunset (common local precaution).
        return Times(minutes(fajr), minutes(rise), minutes(dhuhr, 1), minutes(asrT), minutes(set, 1), minutes(isha))
    }

    // ---------------- Hijri ----------------

    data class Hijri(val year: Int, val month: Int, val day: Int) {
        val monthName: String get() = MONTHS[month - 1]
        val monthNameBn: String get() = MONTHS_BN[month - 1]
        val isRamadan: Boolean get() = month == 9
    }

    val MONTHS = listOf("Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah")
    val MONTHS_BN = listOf("মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ")

    /** Tabular Islamic calendar; [adjustDays] moves it to match local moon sighting (Bangladesh often −1 vs. Saudi). */
    fun hijri(year: Int, month: Int, day: Int, adjustDays: Int = 0): Hijri {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        val jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045 + adjustDays
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val hm = (24 * l) / 709
        val hd = l - (709 * hm) / 24
        val hy = 30 * n + j - 30
        return Hijri(hy, hm, hd)
    }

    // ---------------- Qibla ----------------

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262

    /** Direction of the Kaaba in degrees from true north, clockwise (Dhaka ≈ 278°, i.e. west a bit north). */
    fun qibla(lat: Double, lng: Double): Double {
        val dLng = dtr(KAABA_LNG - lng)
        val y = sin(dLng)
        val x = cos(dtr(lat)) * tan(dtr(KAABA_LAT)) - sin(dtr(lat)) * cos(dLng)
        return fixAngle(rtd(atan2(y, x)))
    }

    fun clock(min: Int): String = if (min < 0) "—" else {
        val h = min / 60; val m = min % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }

    val NAMES_BN = mapOf("Fajr" to "ফজর", "Sunrise" to "সূর্যোদয়", "Dhuhr" to "যোহর", "Asr" to "আসর", "Maghrib" to "মাগরিব", "Isha" to "এশা")
}
