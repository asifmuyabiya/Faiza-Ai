package com.faiza.ai.ai.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic's Claude (Messages API), used by Code Studio when the owner adds a Claude key.
 * Text and images only — Code Studio's agents don't use tool calls.
 */
class ClaudeProvider(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val client: OkHttpClient = AIProviders.http,
    private val maxTokens: Int = 32_000
) : AIProvider {

    override suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply = withContext(Dispatchers.IO) {
        val messages = JsonArray()
        history.forEach { turn ->
            when (turn) {
                is ChatTurn.User -> messages.add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        turn.imageJpegBase64?.let { img ->
                            add(JsonObject().apply {
                                addProperty("type", "image")
                                add("source", JsonObject().apply {
                                    addProperty("type", "base64")
                                    addProperty("media_type", "image/jpeg")
                                    addProperty("data", img)
                                })
                            })
                        }
                        add(JsonObject().apply { addProperty("type", "text"); addProperty("text", turn.text.ifBlank { "." }) })
                    })
                })
                is ChatTurn.Assistant -> messages.add(JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", turn.text.orEmpty().ifBlank { "." })
                })
                is ChatTurn.ToolResult -> messages.add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "[${turn.name}] ${turn.result}")
                })
            }
        }
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", maxTokens)
            addProperty("system", system)
            add("messages", messages)
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching {
                    JsonParser.parseString(text).asJsonObject.getAsJsonObject("error").get("message").asString
                }.getOrDefault(text.take(300))
                throw AIProviderException("Claude: $msg", res.code)
            }
            val json = JsonParser.parseString(text).asJsonObject
            val out = json.getAsJsonArray("content")
                ?.filter { it.asJsonObject.get("type")?.asString == "text" }
                ?.joinToString("") { it.asJsonObject.get("text").asString }
            val usage = json.getAsJsonObject("usage")
            AIReply(
                out, emptyList(), null,
                usage?.get("input_tokens")?.asInt ?: 0,
                usage?.get("output_tokens")?.asInt ?: 0
            )
        }
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw AIProviderException("Claude: ${text.take(200)}", res.code)
            JsonParser.parseString(text).asJsonObject.getAsJsonArray("data").map { it.asJsonObject.get("id").asString }
        }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}
