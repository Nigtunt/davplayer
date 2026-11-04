package com.davplayer.app.ios.data

/** 读取本地文件（缩略图等） */
expect fun readFileBytes(path: String): ByteArray?
