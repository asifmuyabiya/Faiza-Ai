package com.faiza.ai.ai.sos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Builds the SOS text (pure function, unit-tested). */
object SosMessage {

    data class Place(val lat: Double, val lng: Double, val accuracyM: Float?, val timeMillis: Long)

    fun build(userName: String?, personalLine: String?, place: Place?, now: Long = System.currentTimeMillis()): String {
        val who = userName?.trim()?.takeIf { it.isNotEmpty() } ?: "I"
        val needs = if (who == "I") "I need help urgently!" else "$who needs help urgently!"
        return buildString {
            append("SOS! ").append(needs)
            personalLine?.trim()?.takeIf { it.isNotEmpty() }?.let { append(' ').append(it) }
            append('\n')
            if (place != null) {
                append("Location: https://maps.google.com/?q=")
                append(String.format(Locale.US, "%.6f,%.6f", place.lat, place.lng))
                val details = mutableListOf<String>()
                place.accuracyM?.let { details += "±${it.roundToInt()} m" }
                val ageMin = ((now - place.timeMillis) / 60_000).toInt()
                details += if (ageMin <= 1) "just now" else "as of ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(place.timeMillis))}"
                append(" (").append(details.joinToString(", ")).append(')')
            } else {
                append("Location unavailable — please call me now.")
            }
            append("\n— sent automatically by Faiza AI")
        }
    }
}
