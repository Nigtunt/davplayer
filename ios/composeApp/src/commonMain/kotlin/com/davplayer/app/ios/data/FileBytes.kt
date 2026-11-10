package com.davplayer.app.ios.data

/** 读取本地文件（缩略图等） */
expect fun readFileBytes(path: String): ByteArray?

/** 写入本地文件（图片缓存等） */
expect fun writeFileBytes(path: String, bytes: ByteArray): Boolean

/** 图片磁盘缓存目录 */
expect fun imageCacheDir(): String?
