package com.davplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("serverUrl", "") ?: ""
        set(v) = sp.edit().putString("serverUrl", v).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(v) = sp.edit().putString("username", v).apply()

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(v) = sp.edit().putString("password", v).apply()

    /** 长按屏幕时的倍速 */
    var longPressSpeed: Float
        get() = sp.getFloat("longPressSpeed", 2.0f)
        set(v) = sp.edit().putFloat("longPressSpeed", v).apply()

    /** 上次浏览的目录 */
    var lastPath: String?
        get() = sp.getString("lastPath", null)
        set(v) = sp.edit().putString("lastPath", v).apply()
}

data class HistoryItem(
    val href: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

class HistoryStore(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "history.json")
    private val coverDir: File get() = File(ctx.filesDir, "covers").apply { mkdirs() }

    fun coverFile(href: String): File = File(coverDir, md5(href) + ".jpg")

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Synchronized
    fun list(): List<HistoryItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryItem(
                    href = o.getString("href"),
                    title = o.optString("title"),
                    positionMs = o.optLong("positionMs"),
                    durationMs = o.optLong("durationMs"),
                    updatedAt = o.optLong("updatedAt")
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun get(href: String): HistoryItem? = list().firstOrNull { it.href == href }

    @Synchronized
    fun upsert(item: HistoryItem) {
        val items = list().filterNot { it.href == item.href }.toMutableList()
        items.add(item)
        val arr = JSONArray()
        items.sortedByDescending { it.updatedAt }.take(200).forEach { h ->
            arr.put(JSONObject().apply {
                put("href", h.href)
                put("title", h.title)
                put("positionMs", h.positionMs)
                put("durationMs", h.durationMs)
                put("updatedAt", h.updatedAt)
            })
        }
        file.writeText(arr.toString())
    }

    @Synchronized
    fun remove(href: String) {
        val arr = JSONArray()
        list().filterNot { it.href == href }.forEach { h ->
            arr.put(JSONObject().apply {
                put("href", h.href)
                put("title", h.title)
                put("positionMs", h.positionMs)
                put("durationMs", h.durationMs)
                put("updatedAt", h.updatedAt)
            })
        }
        file.writeText(arr.toString())
        coverFile(href).delete()
    }
}

fun formatMs(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toFloat())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toFloat())
    else -> "$bytes B"
}
