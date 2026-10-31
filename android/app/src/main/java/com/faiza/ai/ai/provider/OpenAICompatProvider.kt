package com.faiza.ai.ai.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** OpenAI Chat Completions format — also spoken by Groq, OpenRouter, Ollama, LM Studio, etc. */
class OpenAICompatProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : AIProvider {

    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("model", model)
                add("messages", buildMessages(system, history))
                if (tools.isNotEmpty()) add("tools", buildTools(tools))
            }

            val root = execute(
                request("$baseUrl/chat/completions")
                    .post(body.toString().toRequestBody(json))
                    .build()
            )

            val message = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?: throw AIProviderException("Empty reply from $model")

            val calls = message.getAsJsonArray("tool_calls")?.map { el ->
                val call = el.asJsonObject
                val fn = call.getAsJsonObject("function")
                ToolCall(
                    id = call.get("id")?.asString.orEmpty(),
                    name = fn.get("name").asString,
                    args = runCatching {
                        JsonParser.parseString(fn.get("arguments")?.asString ?: "{}").asJsonObject
                    }.getOrDefault(JsonObject())
                )
            }.orEmpty()

            val text = message.get("content")?.takeIf { it.isJsonPrimitive }?.asString
            val usage = root.getAsJsonObject("usage")
            AIReply(
                text = text?.ifBlank { null },
                toolCalls = calls,
                raw = null,
                inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0,
                outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
            )
        }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        execute(request("$baseUrl/models").get().build())
            .getAsJsonArray("data")
            ?.mapNotNull { it.asJsonObject.get("id")?.asString }
            ?.filter { AIProviders.looksLikeChatModel(it) }
            ?.sorted()
            .orEmpty()
    }

    private fun request(url: String) = Request.Builder().url(url).apply {
        if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
        header("X-Title", "Faiza AI")
    }

    private fun execute(request: Request): JsonObject {
        val response = try {
            AIProviders.http.newCall(request).execute()
        } catch (e: IllegalArgumentException) {
            throw AIProviderException("Invalid server URL")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val parsed = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            if (!it.isSuccessful) {
                val err = parsed?.get("error")
                val msg = when {
                    err == null -> "HTTP ${it.code}"
                    err.isJsonObject -> err.asJsonObject.get("message")?.asString ?: "HTTP ${it.code}"
                    else -> err.asString
                }
                throw AIProviderException(msg, it.code)
            }
            return parsed ?: throw AIProviderException("Invalid response from server")
        }
    }

    private fun buildMessages(system: String, history: List<ChatTurn>): JsonArray {
        val messages = JsonArray()
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", system)
        })
        for (turn in history) {
            messages.add(when (turn) {
                is ChatTurn.User -> JsonObject().apply {
                    addProperty("role", "user")
                    if (turn.imageJpegBase64 == null && turn.files.isEmpty()) {
                        addProperty("content", turn.text)
                    } else {
                        add("content", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", turn.text)
                            })
                            fun image(mime: String, data: String) = add(JsonObject().apply {
                                addProperty("type", "image_url")
                                add("image_url", JsonObject().apply { addProperty("url", "data:$mime;base64,$data") })
                            })
                            turn.imageJpegBase64?.let { image("image/jpeg", it) }
                            turn.files.forEach { f ->
                                when {
                                    f.text != null -> add(JsonObject().apply {
                                        addProperty("type", "text")
                                        addProperty("text", "[File: ${f.name}]\n${f.text}")
                                    })
                                    f.base64 != null && f.isImage -> image(f.mimeType, f.base64)
                                    // OpenAI-style PDF input (OpenAI, OpenRouter).
                                    f.base64 != null -> add(JsonObject().apply {
                                        addProperty("type", "file")
                                        add("file", JsonObject().apply {
                                            addProperty("filename", f.name)
                                            addProperty("file_data", "data:${f.mimeType};base64,${f.base64}")
                                        })
                                    })
                                }
                            }
                        })
                    }
                }
                is ChatTurn.Assistant -> JsonObject().apply {
                    addProperty("role", "assistant")
                    if (turn.text != null) addProperty("content", turn.text) else add("content", null)
                    if (turn.toolCalls.isNotEmpty()) {
                        add("tool_calls", JsonArray().apply {
                            turn.toolCalls.forEach { call ->
                                add(JsonObject().apply {
                                    addProperty("id", call.id)
                                    addProperty("type", "function")
                                    add("function", JsonObject().apply {
                                        addProperty("name", call.name)
                                        addProperty("arguments", call.args.toString())
                                    })
                                })
                            }
                        })
                    }
                }
                is ChatTurn.ToolResult -> JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", turn.callId)
                    addProperty("content", turn.result)
                }
            })
        }
        return messages
    }

    private fun buildTools(tools: List<ToolSpec>): JsonArray = JsonArray().apply {
        tools.forEach { spec ->
            add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", spec.name)
                    addProperty("description", spec.description)
                    add("parameters", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            spec.params.forEach { p ->
                                add(p.name, JsonObject().apply {
                                    addProperty("type", p.type.name.lowercase())
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
                })
            })
        }
    }
}
