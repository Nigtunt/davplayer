package com.davplayer.app.ios.data

import java.io.File

actual fun readFileBytes(path: String): ByteArray? =
    runCatching {
        val file = File(path)
        if (file.isFile) file.readBytes() else null
    }.getOrNull()
