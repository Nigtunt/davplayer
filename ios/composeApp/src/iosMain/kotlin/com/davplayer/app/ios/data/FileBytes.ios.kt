package com.davplayer.app.ios.data

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return ByteArray(0)
    val raw = data.bytes ?: return null
    return raw.reinterpret<ByteVar>().readBytes(length)
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    return bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            .writeToFile(path, atomically = true)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun imageCacheDir(): String? {
    val base = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String ?: return null
    val dir = "$base/DavPlayerImages"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    return dir
}
