package com.davplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class DavSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val user: String,
    val pass: String
)

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- 多 WebDAV 源 ----------

    fun getSources(): List<DavSource> {
        val json = sp.getString("sources", null)
        if (json != null) {
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    DavSource(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = o.optString("name"),
                        url = o.optString("url"),
                        user = o.optString("user"),
                        pass = o.optString("pass")
                    )
                }
            }.getOrDefault(emptyList())
        }
        // 迁移旧的单源配置
        val legacyUrl = sp.getString("serverUrl", "") ?: ""
        if (legacyUrl.isBlank()) return emptyList()
        val migrated = listOf(
            DavSource(
                id = "default",
                name = "默认源",
                url = legacyUrl,
                user = sp.getString("username", "") ?: "",
                pass = sp.getString("password", "") ?: ""
            )
        )
        saveSources(migrated)
        return migrated
    }

    fun saveSources(list: List<DavSource>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("user", s.user)
                put("pass", s.pass)
            })
        }
        sp.edit().putString("sources", arr.toString()).apply()
    }

    var activeSourceId: String?
        get() = sp.getString("activeSourceId", null)
        set(v) = sp.edit().putString("activeSourceId", v).apply()

    fun activeSource(): DavSource? {
        val list = getSources()
        return list.firstOrNull { it.id == activeSourceId } ?: list.firstOrNull()
    }

    /** 长按屏幕时的倍速 */
    var longPressSpeed: Float
        get() = sp.getFloat("longPressSpeed", 2.0f)
        set(v) = sp.edit().putFloat("longPressSpeed", v).apply()

    /** 解码方式："hw" 硬件优先（默认）| "sw" 软件优先（修复部分视频硬解卡顿） */
    var decodeMode: String
        get() = sp.getString("decodeMode", "hw") ?: "hw"
        set(v) = sp.edit().putString("decodeMode", v).apply()

    /** 上次浏览的目录 */
    var lastPath: String?
        get() = sp.getString("lastPath", null)
        set(v) = sp.edit().putString("lastPath", v).apply()

    // ---------- Komga 服务器 ----------

    var komgaUrl: String
        get() = sp.getString("komgaUrl", "") ?: ""
        set(v) = sp.edit().putString("komgaUrl", v).apply()

    var komgaUser: String
        get() = sp.getString("komgaUser", "") ?: ""
        set(v) = sp.edit().putString("komgaUser", v).apply()

    var komgaPass: String
        get() = sp.getString("komgaPass", "") ?: ""
        set(v) = sp.edit().putString("komgaPass", v).apply()

    /** "system" | "light" | "dark" */
    var themeMode: String
        get() = sp.getString("themeMode", "dark") ?: "dark"
        set(v) = sp.edit().putString("themeMode", v).apply()
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

// ---------- 漫画阅读历史 ----------

data class ComicHistoryItem(
    val bookId: String,
    val seriesTitle: String,
    val title: String,
    val page: Int,          // 1-based
    val pagesCount: Int,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (pagesCount > 0) (page.toFloat() / pagesCount).coerceIn(0f, 1f) else 0f
}

class ComicHistoryStore(private val ctx: Context) {

    private val file: File get() = File(ctx.filesDir, "comic_history.json")

    @Synchronized
    fun list(): List<ComicHistoryItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ComicHistoryItem(
                    bookId = o.getString("bookId"),
                    seriesTitle = o.optString("seriesTitle"),
                    title = o.optString("title"),
                    page = o.optInt("page"),
                    pagesCount = o.optInt("pagesCount"),
                    updatedAt = o.optLong("updatedAt")
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun get(bookId: String): ComicHistoryItem? = list().firstOrNull { it.bookId == bookId }

    @Synchronized
    fun upsert(item: ComicHistoryItem) {
        val items = list().filterNot { it.bookId == item.bookId }.toMutableList()
        items.add(item)
        write(items)
    }

    @Synchronized
    fun remove(bookId: String) = write(list().filterNot { it.bookId == bookId })

    private fun write(items: List<ComicHistoryItem>) {
        val arr = JSONArray()
        items.sortedByDescending { it.updatedAt }.take(200).forEach { h ->
            arr.put(JSONObject().apply {
                put("bookId", h.bookId)
                put("seriesTitle", h.seriesTitle)
                put("title", h.title)
                put("page", h.page)
                put("pagesCount", h.pagesCount)
                put("updatedAt", h.updatedAt)
            })
        }
        file.writeText(arr.toString())
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
