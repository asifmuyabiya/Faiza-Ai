package com.faiza.ai.util

import android.content.Context
import android.os.Build
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.AIProviders
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** In-app update check (video 7:15): asks Faiza's server for the newest version. */
object UpdateChecker {

    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val url: String,
        val notes: String,
        val minSupported: Long
    )

    private const val DAY_MS = 24 * 3600_000L

    /** Pure, so it is unit-tested. */
    fun parse(root: JsonObject): UpdateInfo? {
        val code = root.get("versionCode")?.takeIf { it.isJsonPrimitive }?.asLong ?: return null
        return UpdateInfo(
            versionCode = code,
            versionName = root.get("versionName")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().ifBlank { "$code" },
            url = root.get("url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            notes = root.get("notes")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            minSupported = root.get("minSupported")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        )
    }

    fun isNewer(current: Long, info: UpdateInfo): Boolean = info.versionCode > current && info.url.startsWith("http")

    fun isRequired(current: Long, info: UpdateInfo): Boolean = current < info.minSupported

    fun currentVersionCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    fun currentVersionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: FaizaApp.VERSION

    /** null = couldn't reach the server. */
    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${FaizaApp.SERVER_URL.trimEnd('/')}/api/app/latest").get().build()
            AIProviders.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                parse(JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject)
            }
        }.getOrNull()
    }

    /** Checks at most once a day (or now when [force]); remembers a newer version for the dashboard banner. */
    suspend fun check(ctx: Context, force: Boolean = false): UpdateInfo? {
        val prefs = FaizaApp.instance.prefs
        val now = System.currentTimeMillis()
        if (!force && now - prefs.lastUpdateCheckAt < DAY_MS) return savedUpdate(ctx)
        val info = fetch() ?: return savedUpdate(ctx)
        prefs.lastUpdateCheckAt = now
        prefs.availableUpdateJson = if (isNewer(currentVersionCode(ctx), info)) JsonObject().apply {
            addProperty("versionCode", info.versionCode)
            addProperty("versionName", info.versionName)
            addProperty("url", info.url)
            addProperty("notes", info.notes)
            addProperty("minSupported", info.minSupported)
        }.toString() else null
        return info
    }

    /** A newer version found earlier, if it's still newer than what's installed. */
    fun savedUpdate(ctx: Context): UpdateInfo? {
        val json = FaizaApp.instance.prefs.availableUpdateJson ?: return null
        val info = runCatching { parse(JsonParser.parseString(json).asJsonObject) }.getOrNull() ?: return null
        return info.takeIf { isNewer(currentVersionCode(ctx), it) }
    }
}
