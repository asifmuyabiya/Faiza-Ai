package com.faiza.ai.ai.skills

import android.content.Context
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.provider.ToolSpec
import com.google.gson.JsonObject

/**
 * Something Faiza can actually *do*. The AI sees [spec] as a callable tool;
 * [run] performs it and returns a short result string the AI reads back.
 */
class Skill(
    val id: String,
    val emoji: String,
    val title: String,
    val summary: String,
    private val toolDescription: String,
    private val params: List<ToolParam> = emptyList(),
    val needsAccessibility: Boolean = false,
    val run: suspend (Context, JsonObject) -> String
) {
    val spec: ToolSpec get() = ToolSpec(id, toolDescription, params)
}

class SkillException(message: String) : Exception(message)

fun JsonObject.str(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null }

fun JsonObject.int(name: String): Int? =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }

fun JsonObject.bool(name: String): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false

fun JsonObject.require(name: String): String =
    str(name) ?: throw SkillException("missing '$name'")
