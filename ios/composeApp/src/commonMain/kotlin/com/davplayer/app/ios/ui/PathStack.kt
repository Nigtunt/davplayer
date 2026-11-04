package com.davplayer.app.ios.ui

/**
 * 把目标目录展开成 root → ... → target 的完整路径栈。
 * 例如 root=/dav/、target=/dav/115/comic/ -> [/dav/, /dav/115/, /dav/115/comic/]
 *
 * 历史续播/上次路径直接进子目录时，必须补全父级，才能一级级返回根目录。
 */
internal fun buildPathStack(root: String, target: String): List<String> {
    val rootPath = if (root.endsWith("/")) root else "$root/"
    val targetPath = if (target.endsWith("/")) target else "$target/"
    if (!targetPath.startsWith(rootPath)) return listOf(rootPath)

    val relative = targetPath.removePrefix(rootPath).trim('/')
    if (relative.isBlank()) return listOf(rootPath)

    val result = mutableListOf(rootPath)
    var current = rootPath
    relative.split('/').filter { it.isNotBlank() }.forEach { part ->
        current += "$part/"
        result += current
    }
    return result
}
