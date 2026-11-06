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

/**
 * 只保存 Komga 连接配置与安全模式。
 * 漫画阅读进度以 Komga 服务端为准，不再在本地保存页码历史。
 */
object ComicStore {
    private const val KEY_CONFIG = "komga.config.v1"
    private const val KEY_SAFE_MODE = "komga.safeMode"

    /** 安全模式：不加载任何 Komga 图片，用于排查 iPad 上的 Skia 崩溃 */
    var safeMode: Boolean by mutableStateOf(readSetting(KEY_SAFE_MODE) == "1")
        private set

    fun updateSafeMode(enabled: Boolean) {
        safeMode = enabled
        writeSetting(KEY_SAFE_MODE, if (enabled) "1" else "0")
    }

    var config: KomgaConfig by mutableStateOf(loadConfig())
        private set

    fun saveConfig(url: String, email: String, pass: String) {
        config = KomgaConfig(url.trim().trimEnd('/'), email.trim(), pass)
        writeSetting(
            KEY_CONFIG,
            listOf(config.url, config.email, config.pass)
                .joinToString("\t") { percentEncode(it) }
        )
    }

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
}
