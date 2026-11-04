package com.davplayer.app.ios.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        data.getBytes(pinned.addressOf(0), data.length)
    }
    return bytes
}
