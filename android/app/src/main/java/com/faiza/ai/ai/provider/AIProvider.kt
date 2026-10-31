package com.faiza.ai.ai.provider

import com.faiza.ai.util.PrefsManager
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ====== Conversation model shared by all providers ======

/**
 * A file the user attached in Chat (video 30:51): an image or PDF travels as [base64];
 * a text file travels as [text] so every provider can read it.
 */
data class Attachment(
    val name: String,
    val mimeType: String,
    val base64: String? = null,
    val text: String? = null
) {
    val isImage get() = mimeType.startsWith("image/")
    val isPdf get() = mimeType == "application/pdf"
}

sealed class ChatTurn {
    data class User(
        val text: String,
        val imageJpegBase64: String? = null,
        val files: List<Attachment> = emptyList()
    ) : ChatTurn()

    /** [raw] is the provider's own message object, replayed verbatim (Gemini needs its thought signatures back). */
    data class Assistant(
        val text: String?,
        val toolCalls: List<ToolCall> = emptyList(),
        val raw: JsonObject? = null
    ) : ChatTurn()

    data class ToolResult(val callId: String, val name: String, val result: String) : ChatTurn()
}

data class ToolCall(val id: String, val name: String, val args: JsonObject)

enum class ParamType { STRING, INTEGER, NUMBER, BOOLEAN }

data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

data class ToolSpec(val name: String, val description: String, val params: List<ToolParam> = emptyList())

data class AIReply(
    val text: String?,
    val toolCalls: List<ToolCall>,
    val raw: JsonObject?,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

class AIProviderException(message: String, val httpCode: Int? = null) : Exception(message) {
    val isAuthError get() = httpCode == 401 || httpCode == 403 ||
            (httpCode == 400 && message?.contains("API key", ignoreCase = true) == true)
    val isRateLimit get() = httpCode == 429
}

interface AIProvider {
    suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply

    /** Chat-capable model ids; doubles as an API-key check. */
    suspend fun listModels(): List<String>
}

// ====== Provider catalogue ======

enum class ProviderType(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val keyHint: String,
    val isPro: Boolean
) {
    GEMINI(
        "gemini", "Google Gemini (Free)",
        "https://generativelanguage.googleapis.com/v1beta",
        "gemini-3.5-flash-lite", "https://aistudio.google.com/app/apikey", "AIza...", isPro = false
    ),
    OPENAI(
        "openai", "OpenAI (ChatGPT)", "https://api.openai.com/v1",
        "", "https://platform.openai.com/api-keys", "sk-...", isPro = true
    ),
    GROQ(
        "groq", "Groq (Super fast)", "https://api.groq.com/openai/v1",
        "", "https://console.groq.com/keys", "gsk_...", isPro = true
    ),
    OPENROUTER(
        "openrouter", "OpenRouter (100+ models)", "https://openrouter.ai/api/v1",
        "", "https://openrouter.ai/keys", "sk-or-...", isPro = true
    ),
    CUSTOM(
        "custom", "Custom (OpenAI-compatible)", "",
        "", "", "API key (optional for local servers)", isPro = true
    );

    companion object {
        fun from(id: String?): ProviderType = values().firstOrNull { it.id == id } ?: GEMINI
    }
}

object AIProviders {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val NON_CHAT_MODEL_WORDS = listOf(
        "embed", "tts", "image", "live", "transcribe", "whisper", "dall-e", "audio",
        "realtime", "moderation", "robotics", "omni", "guard", "aqa"
    )

    fun looksLikeChatModel(id: String): Boolean {
        val lower = id.lowercase()
        return NON_CHAT_MODEL_WORDS.none { lower.contains(it) }
    }

    fun create(
        type: ProviderType,
        apiKey: String,
        model: String,
        customBaseUrl: String? = null
    ): AIProvider = when (type) {
        ProviderType.GEMINI -> GeminiProvider(apiKey, model.ifBlank { type.defaultModel })
        ProviderType.CUSTOM -> OpenAICompatProvider(customBaseUrl.orEmpty().trimEnd('/'), apiKey, model)
        else -> OpenAICompatProvider(type.baseUrl, apiKey, model)
    }

    /** The provider the user has selected, or null if it isn't fully configured yet. */
    fun fromPrefs(prefs: PrefsManager): AIProvider? {
        val type = ProviderType.from(prefs.aiProvider)
        val key = prefs.apiKeyFor(type).orEmpty()
        val model = prefs.modelFor(type)
        return when {
            type == ProviderType.CUSTOM && prefs.customApiUrl.isNullOrBlank() -> null
            type != ProviderType.CUSTOM && key.isBlank() -> null
            type != ProviderType.GEMINI && model.isBlank() -> null
            else -> create(type, key, model, prefs.customApiUrl)
        }
    }
}
