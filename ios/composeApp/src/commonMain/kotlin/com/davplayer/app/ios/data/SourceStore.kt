package com.davplayer.app.ios.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** 一个 WebDAV 源（与 Android 端多源配置对应） */
data class DavSource(
    val id: String,
    val name: String,
    val url: String,
    val user: String,
    val pass: String
)

/** 播放进度：续播与“继续观看”用 */
data class PlayProgress(
    val sourceId: String,
    val path: String,
    val name: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** 本地缩略图路径（Swift 截帧后写入）；为空时用占位预览 */
    val coverPath: String? = null
) {
    /** 0f..1f，未知时长时返回 0 */
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * 多源与播放历史的本地存储。
 * 用简单的制表符分隔 + 百分号转义，避免为一个字段引入序列化插件。
 */
object SourceStore {
    private const val KEY_SOURCES = "sources.v1"
    private const val KEY_HISTORY = "history.v1"
    private const val MAX_HISTORY = 60

    var sources: List<DavSource> by mutableStateOf(loadSources())
        private set

    var history: List<PlayProgress> by mutableStateOf(loadHistory())
        private set

    fun newId(): String = Random.nextLong().toString()

    fun sourceById(id: String): DavSource? = sources.firstOrNull { it.id == id }

    fun addOrUpdate(source: DavSource) {
        val next = if (sources.any { it.id == source.id }) {
            sources.map { if (it.id == source.id) source else it }
        } else {
            sources + source
        }
        sources = next
        writeSetting(KEY_SOURCES, next.joinToString("\n") { encodeSource(it) })
    }

    fun remove(sourceId: String) {
        sources = sources.filterNot { it.id == sourceId }
        history = history.filterNot { it.sourceId == sourceId }
        writeSetting(KEY_SOURCES, sources.joinToString("\n") { encodeSource(it) })
        writeSetting(KEY_HISTORY, history.joinToString("\n") { encodeProgress(it) })
    }

    fun record(
        sourceId: String,
        path: String,
        name: String,
        positionMs: Long,
        durationMs: Long,
        coverPath: String? = null
    ) {
        val previous = history.firstOrNull { it.sourceId == sourceId && it.path == path }
        val item = PlayProgress(
            sourceId = sourceId,
            path = path,
            name = name,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = nowMillis(),
            coverPath = coverPath ?: previous?.coverPath
        )
        val next = (listOf(item) + history.filterNot { it.sourceId == sourceId && it.path == path })
            .sortedByDescending { it.updatedAt }
            .take(MAX_HISTORY)
        history = next
        writeSetting(KEY_HISTORY, next.joinToString("\n") { encodeProgress(it) })
    }

    fun progressFor(sourceId: String, path: String): PlayProgress? =
        history.firstOrNull { it.sourceId == sourceId && it.path == path }

    /** 上次浏览目录（按源隔离） */
    fun lastPath(sourceId: String): String? =
        readSetting("lastPath.$sourceId")?.takeIf { it.isNotBlank() }

    fun setLastPath(sourceId: String, path: String) {
        writeSetting("lastPath.$sourceId", path)
    }

    fun recent(limit: Int = 8): List<PlayProgress> = history.take(limit)

    // ---- 存储格式 ----

    private fun encodeSource(s: DavSource): String =
        listOf(s.id, s.name, s.url, s.user, s.pass).joinToString("\t") { percentEncode(it) }

    private fun encodeProgress(p: PlayProgress): String = listOf(
        p.sourceId,
        p.path,
        p.name,
        p.positionMs.toString(),
        p.durationMs.toString(),
        p.updatedAt.toString(),
        p.coverPath.orEmpty()
    ).joinToString("\t") { percentEncode(it) }

    private fun loadSources(): List<DavSource> {
        val raw = readSetting(KEY_SOURCES).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val f = line.split('\t').map { percentDecode(it) }
            if (f.size < 5 || f[0].isBlank()) return@mapNotNull null
            DavSource(id = f[0], name = f[1], url = f[2], user = f[3], pass = f[4])
        }.toList()
    }

    private fun loadHistory(): List<PlayProgress> {
        val raw = readSetting(KEY_HISTORY).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val f = line.split('\t').map { percentDecode(it) }
            if (f.size < 6) return@mapNotNull null
            PlayProgress(
                sourceId = f[0],
                path = f[1],
                name = f[2],
                positionMs = f[3].toLongOrNull() ?: 0L,
                durationMs = f[4].toLongOrNull() ?: 0L,
                updatedAt = f[5].toLongOrNull() ?: 0L,
                coverPath = f.getOrNull(6)?.takeIf { it.isNotBlank() }
            )
        }.sortedByDescending { it.updatedAt }.toList()
    }
}

/** 百分号编码：只保留 URL 安全字符，避免制表符/换行破坏存储格式 */
fun percentEncode(s: String): String {
    val bytes = s.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 3)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        val c = v.toChar()
        val safe = v < 128 && (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~')
        if (safe) {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append(HEX[v shr 4])
            sb.append(HEX[v and 0x0F])
        }
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"
