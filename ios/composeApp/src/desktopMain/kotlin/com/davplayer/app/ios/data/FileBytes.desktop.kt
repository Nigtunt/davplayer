package com.davplayer.app.ios.data

import java.io.File

actual fun readFileBytes(path: String): ByteArray? =
    runCatching {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }.getOrNull()

actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean =
    runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        true
    }.getOrDefault(false)

actual fun imageCacheDir(): String? =
    File(System.getProperty("java.io.tmpdir"), "davplayer-images")
        .apply { mkdirs() }
        .absolutePath
