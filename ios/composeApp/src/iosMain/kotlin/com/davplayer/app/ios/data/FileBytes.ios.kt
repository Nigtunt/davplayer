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
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
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

@OptIn(ExperimentalForeignApi::class)
actual fun imageCacheSizeBytes(): Long {
    val dir = imageCacheDir() ?: return 0L
    val fm = NSFileManager.defaultManager
    val names = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return 0L
    return names.sumOf { raw ->
        val name = raw as? String ?: return@sumOf 0L
        val attrs = fm.attributesOfItemAtPath("$dir/$name", error = null)
        (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun clearImageCache(): Boolean {
    val dir = imageCacheDir() ?: return false
    val fm = NSFileManager.defaultManager
    val names = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return false
    var ok = true
    names.forEach { raw ->
        val name = raw as? String ?: return@forEach
        if (!fm.removeItemAtPath("$dir/$name", error = null)) ok = false
    }
    return ok
}
