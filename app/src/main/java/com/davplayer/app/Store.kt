package com.davplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

private const val PRIVACY_PIN_ITERATIONS = 4096
private const val LEGACY_PRIVACY_SALT = "davplayer-legacy-pin-v1"
// 旧版默认暗码的加盐摘要，兼容未配置过自定义暗码的老用户；不保存明文。
private const val LEGACY_PRIVACY_PIN_HASH =
    "7bbbb5eeffc6a1c7769089b9657a0e70efc300be4b3bd3d0ab8e99299f3ab130"
// 兼容更早版本内置暗码的摘要，避免老用户升级后被锁在门外。
private const val OLD_LEGACY_PRIVACY_PIN_HASH =
    "d929db26592328c6c9a581f6149b27dadc3dd5649b802049b8e1b89213dbb907"

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

    /** 上次移动文件的目标目录（按源隔离） */
    fun lastMovePath(sourceId: String): String? =
        sp.getString("lastMovePath.$sourceId", null)?.takeIf { it.isNotBlank() }

    fun setLastMovePath(sourceId: String, path: String) {
        sp.edit().putString("lastMovePath.$sourceId", path).apply()
    }

    /** 浏览页文件夹视图：列表 / 图标 */
    var browseGridView: Boolean
        get() = sp.getBoolean("browseGridView", false)
        set(v) = sp.edit().putBoolean("browseGridView", v).apply()

    /** name | size | modified */
    var browseSortMode: String
        get() = sp.getString("browseSortMode", "name") ?: "name"
        set(v) = sp.edit().putString("browseSortMode", v).apply()

    /** 隐私模式：开启后启动显示计算器，输入暗码才进入应用；默认关闭 */
    var privacyMode: Boolean
        get() = sp.getBoolean("privacyMode", false)
        set(v) = sp.edit().putBoolean("privacyMode", v).apply()

    // ---------- 隐私暗码：只保存加盐哈希，不落明文 ----------

    fun hasPrivacyPin(): Boolean =
        !sp.getString("privacyPinHash", null).isNullOrBlank()

    /** 设置/修改暗码；返回是否成功。PIN 只保存加盐 SHA-256 摘要。 */
    fun setPrivacyPin(pin: String): Boolean {
        if (!pin.matches(Regex("^[1-9]\\d{5,11}$"))) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val hash = derivePrivacyPin(pin, salt, PRIVACY_PIN_ITERATIONS)
        sp.edit()
            .putString("privacyPinSalt", salt)
            .putString("privacyPinHash", hash)
            .putInt("privacyPinIterations", PRIVACY_PIN_ITERATIONS)
            .apply()
        return true
    }

    fun verifyPrivacyPin(pin: String): Boolean {
        if (!pin.matches(Regex("^[1-9]\\d{5,11}$"))) return false
        val storedHash = sp.getString("privacyPinHash", null)
        if (!storedHash.isNullOrBlank()) {
            val salt = sp.getString("privacyPinSalt", null) ?: return false
            val iterations = sp.getInt("privacyPinIterations", PRIVACY_PIN_ITERATIONS)
                .coerceAtLeast(1)
            return constantTimeEquals(
                storedHash,
                derivePrivacyPin(pin, salt, iterations)
            )
        }
        // 旧版本没有保存过自定义暗码的用户，继续兼容旧默认暗码。
        // 这里只比较摘要，不在代码/设置界面写明文。
        val derived = derivePrivacyPin(pin, LEGACY_PRIVACY_SALT, PRIVACY_PIN_ITERATIONS)
        return constantTimeEquals(LEGACY_PRIVACY_PIN_HASH, derived) ||
            constantTimeEquals(OLD_LEGACY_PRIVACY_PIN_HASH, derived)
    }

    private fun derivePrivacyPin(pin: String, salt: String, iterations: Int): String {
        var digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        repeat(iterations - 1) {
            digest = MessageDigest.getInstance("SHA-256").digest(digest)
        }
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

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

    /** "system" | "light" | "dark"；默认跟随系统 */
    var themeMode: String
        get() = sp.getString("themeMode", "system") ?: "system"
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
