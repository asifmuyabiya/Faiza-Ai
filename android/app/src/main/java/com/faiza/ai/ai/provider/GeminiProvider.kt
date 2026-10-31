package com.faiza.ai.ai.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiProvider(
    private val apiKey: String,
    private val model: String,
    /** Long jobs (deep research with a Pro model) pass a client with a longer read timeout. */
    private val client: okhttp3.OkHttpClient = AIProviders.http,
    /** Code Studio: think hard and allow very long answers (whole projects). */
    private val deepThinking: Boolean = false
) : AIProvider {

    private val base = ProviderType.GEMINI.baseUrl
    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * MAYA's "sub-agent brain": when this model is out of free quota (429) or overloaded (5xx), quietly try
     * other Gemini Flash models with the same key, newest first.
     */
    override suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply {
        try {
            return chatWith(model, system, history, tools)
        } catch (e: AIProviderException) {
            if (!switchable(e) || !com.faiza.ai.FaizaApp.instance.prefs.isAutoSwitchModels) throw e
            for (other in alternatives()) {
                try {
                    return chatWith(other, system, history, tools)
                } catch (e2: AIProviderException) {
                    if (!switchable(e2)) break
                }
            }
            throw e
        }
    }

    private fun switchable(e: AIProviderException) = e.httpCode == 429 || (e.httpCode ?: 0) >= 500

    @Volatile private var fallbackModels: List<String>? = null

    private suspend fun alternatives(): List<String> {
        fallbackModels?.let { return it }
        val list = runCatching { listModels() }.getOrDefault(emptyList())
            .filter { it != model && it.contains("flash") && !it.contains("exp") }
            .sortedDescending()
            .take(4)
        return list.also { fallbackModels = it }
    }

    private suspend fun chatWith(model: String, system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply { add(textPart(system)) })
                })
                add("contents", buildContents(history))
                if (tools.isNotEmpty()) add("tools", buildTools(tools))
                add("generationConfig", JsonObject().apply {
                    // On Gemini 3, the output limit includes thinking tokens, so keep thinking low
                    // for snappy voice replies instead of capping maxOutputTokens.
                    if (deepThinking) {
                        addProperty("maxOutputTokens", 65_536)
                        if (model.startsWith("gemini-3")) add("thinkingConfig", JsonObject().apply { addProperty("thinkingLevel", "high") })
                    } else if (model.startsWith("gemini-3")) {
                        add("thinkingConfig", JsonObject().apply { addProperty("thinkingLevel", "low") })
                    }
                })
            }

            val request = Request.Builder()
                .url("$base/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(json))
                .build()

            val root = execute(request)
            val candidate = root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?: throw AIProviderException(
                    "No reply (blocked: ${root.getAsJsonObject("promptFeedback")?.get("blockReason")?.asString ?: "unknown"})"
                )
            val content = candidate.getAsJsonObject("content") ?: JsonObject()

            val texts = mutableListOf<String>()
            val calls = mutableListOf<ToolCall>()
            content.getAsJsonArray("parts")?.forEach { el ->
                val part = el.asJsonObject
                if (part.get("thought")?.asBoolean == true) return@forEach
                part.get("text")?.asString?.let { texts += it }
                part.getAsJsonObject("functionCall")?.let { fc ->
                    calls += ToolCall(
                        id = fc.get("id")?.asString.orEmpty(),
                        name = fc.get("name").asString,
                        args = fc.getAsJsonObject("args") ?: JsonObject()
                    )
                }
            }

            val usage = root.getAsJsonObject("usageMetadata")
            AIReply(
                text = texts.joinToString("").ifBlank { null },
                toolCalls = calls,
                raw = content.apply { if (!has("role")) addProperty("role", "model") },
                inputTokens = usage?.get("promptTokenCount")?.asInt ?: 0,
                outputTokens = usage?.get("candidatesTokenCount")?.asInt ?: 0
            )
        }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/models?pageSize=1000")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        execute(request).getAsJsonArray("models")
            ?.map { it.asJsonObject }
            ?.filter { m ->
                m.getAsJsonArray("supportedGenerationMethods")?.any { it.asString == "generateContent" } == true
            }
            ?.map { it.get("name").asString.removePrefix("models/") }
            ?.filter { it.startsWith("gemini") && AIProviders.looksLikeChatModel(it) }
            .orEmpty()
    }

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val msg = parsed?.getAsJsonObject("error")?.get("message")?.asString
                    ?: "HTTP ${response.code}"
                throw AIProviderException(msg, response.code)
            }
            return parsed ?: throw AIProviderException("Invalid response from Gemini")
        }
    }

    private fun buildContents(history: List<ChatTurn>): JsonArray {
        val contents = JsonArray()
        val pendingResults = JsonArray()

        fun flushResults() {
            if (pendingResults.size() == 0) return
            contents.add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", pendingResults.deepCopy())
            })
            while (pendingResults.size() > 0) pendingResults.remove(0)
        }

        for (turn in history) {
            when (turn) {
                is ChatTurn.ToolResult -> pendingResults.add(JsonObject().apply {
                    add("functionResponse", JsonObject().apply {
                        if (turn.callId.isNotEmpty()) addProperty("id", turn.callId)
                        addProperty("name", turn.name)
                        add("response", JsonObject().apply { addProperty("result", turn.result) })
                    })
                })
                is ChatTurn.User -> {
                    flushResults()
                    contents.add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            turn.imageJpegBase64?.let { img ->
                                add(JsonObject().apply {
                                    add("inlineData", JsonObject().apply {
                                        addProperty("mimeType", "image/jpeg")
                                        addProperty("data", img)
                                    })
                                })
                            }
                            // Gemini reads images and PDFs natively; text files go in as text.
                            turn.files.forEach { f ->
                                when {
                                    f.text != null -> add(textPart("[File: ${f.name}]\n${f.text}"))
                                    f.base64 != null -> add(JsonObject().apply {
                                        add("inlineData", JsonObject().apply {
                                            addProperty("mimeType", f.mimeType)
                                            addProperty("data", f.base64)
                                        })
                                    })
                                }
                            }
                            add(textPart(turn.text))
                        })
                    })
                }
                is ChatTurn.Assistant -> {
                    flushResults()
                    contents.add(turn.raw ?: JsonObject().apply {
                        addProperty("role", "model")
                        add("parts", JsonArray().apply { add(textPart(turn.text.orEmpty())) })
                    })
                }
            }
        }
        flushResults()
        return contents
    }

    private fun buildTools(tools: List<ToolSpec>): JsonArray = geminiToolsJson(tools)

    private fun textPart(text: String) = JsonObject().apply { addProperty("text", text) }
}

/** Gemini `tools` array (functionDeclarations) — shared by generateContent and the Live API. */
internal fun geminiToolsJson(tools: List<ToolSpec>): JsonArray {
        val declarations = JsonArray()
        tools.forEach { spec ->
            declarations.add(JsonObject().apply {
                addProperty("name", spec.name)
                addProperty("description", spec.description)
                if (spec.params.isNotEmpty()) {
                    add("parameters", JsonObject().apply {
                        addProperty("type", "OBJECT")
                        add("properties", JsonObject().apply {
                            spec.params.forEach { p ->
                                add(p.name, JsonObject().apply {
                                    addProperty("type", p.type.name)
                                    addProperty("description", p.description)
                                    p.enum?.let { values ->
                                        add("enum", JsonArray().apply { values.forEach { add(it) } })
                                    }
                                })
                            }
                        })
                        add("required", JsonArray().apply {
                            spec.params.filter { it.required }.forEach { add(it.name) }
                        })
                    })
                }
            })
        }
        return JsonArray().apply {
            add(JsonObject().apply { add("functionDeclarations", declarations) })
        }
}
