package com.faiza.ai.ai.guardian

import android.content.Context
import com.faiza.ai.ai.wake.VoskRuntime
import com.faiza.ai.util.PrefsManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.vosk.Recognizer
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A remembered voice: the owner, or a family member allowed in "me + family" mode. */
data class VoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** "owner" or "family" */
    val role: String,
    val vector: List<Float>,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isOwner get() = role == ROLE_OWNER

    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_FAMILY = "family"
    }
}

/** Voice prints ("x-vectors") from Vosk's speaker model, plus storage and matching. */
object VoicePrint {

    const val SAMPLE_RATE = 16_000f
    /** Vosk counts 10 ms frames; below ~0.8 s of speech a voice print isn't reliable. */
    const val MIN_RELIABLE_FRAMES = 80

    private val gson = Gson()

    data class Print(val vector: FloatArray, val frames: Int)

    /** Voice print of a whole recording (16 kHz mono PCM16). Call off the main thread. */
    fun extract(ctx: Context, pcm: ByteArray): Print? {
        val rec = Recognizer(VoskRuntime.speechModel(ctx), SAMPLE_RATE, VoskRuntime.speakerModel(ctx))
        try {
            val parts = mutableListOf<Print>()
            var offset = 0
            while (offset < pcm.size) {
                val len = minOf(3_200, pcm.size - offset)
                val chunk = pcm.copyOfRange(offset, offset + len)
                if (rec.acceptWaveForm(chunk, len)) parse(rec.result)?.let { parts += it }
                offset += len
            }
            parse(rec.finalResult)?.let { parts += it }
            return combine(parts)
        } finally {
            runCatching { rec.close() }
        }
    }

    /** Reads "spk" + "spk_frames" from a Vosk result. */
    fun parse(json: String?): Print? {
        if (json.isNullOrBlank()) return null
        val o = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val spk = o.getAsJsonArray("spk") ?: return null
        val frames = o.get("spk_frames")?.asInt ?: 0
        if (spk.size() == 0 || frames <= 0) return null
        return Print(FloatArray(spk.size()) { spk[it].asFloat }, frames)
    }

    /** Frame-weighted average of several prints (unit-normalised). */
    fun combine(prints: List<Print>): Print? {
        val usable = prints.filter { it.frames > 0 && it.vector.isNotEmpty() }
        if (usable.isEmpty()) return null
        val size = usable.first().vector.size
        val sum = FloatArray(size)
        var frames = 0
        for (p in usable) {
            if (p.vector.size != size) continue
            val unit = normalize(p.vector)
            for (i in 0 until size) sum[i] += unit[i] * p.frames
            frames += p.frames
        }
        return Print(normalize(sum), frames)
    }

    fun normalize(v: FloatArray): FloatArray {
        var n = 0.0
        for (x in v) n += x * x
        val len = sqrt(n).toFloat()
        return if (len == 0f) v.copyOf() else FloatArray(v.size) { v[it] / len }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Similarity shown to the user as a percentage (0–100). */
    fun percent(a: FloatArray, b: FloatArray): Int = (cosine(a, b) * 100).roundToInt().coerceIn(0, 100)

    data class Match(val profile: VoiceProfile, val percent: Int)

    fun bestMatch(print: FloatArray, profiles: List<VoiceProfile>): Match? =
        profiles.map { Match(it, percent(print, it.vector.toFloatArray())) }.maxByOrNull { it.percent }

    // ===== Storage =====

    fun load(prefs: PrefsManager): List<VoiceProfile> {
        val json = prefs.voiceProfilesJson ?: return emptyList()
        @Suppress("SENSELESS_COMPARISON")
        return runCatching {
            gson.fromJson<List<VoiceProfile>>(json, object : TypeToken<List<VoiceProfile>>() {}.type)
                .filter { it != null && it.name != null && it.vector != null && it.vector.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: PrefsManager, profiles: List<VoiceProfile>) {
        prefs.voiceProfilesJson = gson.toJson(profiles)
    }
}
