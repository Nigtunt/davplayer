package com.davplayer.app.ios.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class KomgaConfig(
    val url: String = "",
    val email: String = "",
    val pass: String = ""
) {
    val isConfigured: Boolean get() = url.isNotBlank()
}

data class ComicProgress(
    val bookId: String,
    val seriesTitle: String,
    val title: String,
    val page: Int,
    val pagesCount: Int,
    val updatedAt: Long
) {
    val fraction: Float
        get() = if (pagesCount > 0) (page.toFloat() / pagesCount).coerceIn(0f, 1f) else 0f
}

/** Komga 服务器配置 + 漫画阅读历史 */
object ComicStore {
    private const val KEY_CONFIG = "komga.config.v1"
    private const val KEY_HISTORY = "comic.history.v1"
    private const val KEY_SAFE_MODE = "komga.safeMode"
    private const val MAX_HISTORY = 200

    /** 安全模式：不加载任何 Komga 图片，用于排查 iPad 上的 Skia 崩溃 */
    var safeMode: Boolean by mutableStateOf(readSetting(KEY_SAFE_MODE) == "1")
        private set

    fun updateSafeMode(enabled: Boolean) {
        safeMode = enabled
        writeSetting(KEY_SAFE_MODE, if (enabled) "1" else "0")
    }

    var config: KomgaConfig by mutableStateOf(loadConfig())
        private set

    var history: List<ComicProgress> by mutableStateOf(loadHistory())
        private set

    fun saveConfig(url: String, email: String, pass: String) {
        config = KomgaConfig(url.trim().trimEnd('/'), email.trim(), pass)
        writeSetting(
            KEY_CONFIG,
            listOf(config.url, config.email, config.pass)
                .joinToString("\t") { percentEncode(it) }
        )
    }

    fun record(
        bookId: String,
        seriesTitle: String,
        title: String,
        page: Int,
        pagesCount: Int
    ) {
        val item = ComicProgress(
            bookId = bookId,
            seriesTitle = seriesTitle,
            title = title,
            page = page.coerceAtLeast(0),
            pagesCount = pagesCount.coerceAtLeast(0),
            updatedAt = nowMillis()
        )
        history = (listOf(item) + history.filterNot { it.bookId == bookId })
            .sortedByDescending { it.updatedAt }
            .take(MAX_HISTORY)
        writeSetting(KEY_HISTORY, history.joinToString("\n") { encode(it) })
        AppLog.info("Comic", "${title.take(24)} 第 $page/$pagesCount 页")
    }

    fun progressFor(bookId: String): ComicProgress? =
        history.firstOrNull { it.bookId == bookId }

    fun remove(bookId: String) {
        history = history.filterNot { it.bookId == bookId }
        writeSetting(KEY_HISTORY, history.joinToString("\n") { encode(it) })
    }

    private fun encode(p: ComicProgress): String = listOf(
        p.bookId, p.seriesTitle, p.title, p.page.toString(), p.pagesCount.toString(), p.updatedAt.toString()
    ).joinToString("\t") { percentEncode(it) }

    private fun loadConfig(): KomgaConfig {
        val raw = readSetting(KEY_CONFIG).orEmpty()
        if (raw.isBlank()) return KomgaConfig()
        val f = raw.split('\t').map { percentDecode(it) }
        return KomgaConfig(
            url = f.getOrNull(0).orEmpty(),
            email = f.getOrNull(1).orEmpty(),
            pass = f.getOrNull(2).orEmpty()
        )
    }

    private fun loadHistory(): List<ComicProgress> {
        val raw = readSetting(KEY_HISTORY).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val f = line.split('\t').map { percentDecode(it) }
            if (f.size < 6) return@mapNotNull null
            ComicProgress(
                bookId = f[0],
                seriesTitle = f[1],
                title = f[2],
                page = f[3].toIntOrNull() ?: 0,
                pagesCount = f[4].toIntOrNull() ?: 0,
                updatedAt = f[5].toLongOrNull() ?: 0L
            )
        }.sortedByDescending { it.updatedAt }.toList()
    }
}
