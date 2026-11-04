package com.davplayer.app.ios.data

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** WebDAV 列表项（与 Android 端 DavEntry 对应） */
data class DavEntry(
    val name: String,
    val href: String,
    val isDir: Boolean,
    val size: Long
)

/**
 * 极简 WebDAV 客户端，只做 PROPFIND Depth:1。
 * 后续稳定后把 Android 的 Dav.kt 与这份实现合并成共享模块。
 */
class DavClient(baseUrl: String, private val user: String, private val pass: String) {

    private val http = HttpClient()

    private val origin: String = run {
        val scheme = if (baseUrl.startsWith("https", true)) "https" else "http"
        val rest = baseUrl.substringAfter("://", baseUrl)
        val host = if (rest.contains('/')) rest.substringBefore('/') else rest
        "$scheme://$host"
    }

    /** 服务器根路径（已编码），如 /dav/ */
    val rootPath: String = run {
        val rest = baseUrl.trim().trimEnd('/').substringAfter("://", baseUrl)
        val slash = rest.indexOf('/')
        val path = if (slash < 0) "/" else rest.substring(slash)
        if (path.endsWith("/")) path else "$path/"
    }

    private val authHeader: String? =
        if (user.isBlank()) null else "Basic " + base64("$user:$pass")

    /**
     * 播放器需要独立的认证信息：VLCKit 通过 media option 传 Basic 认证，
     * 不能把密码拼进 URL（会出现在日志里，且转义容易出错）。
     */
    val authUser: String get() = user
    val authPass: String get() = pass

    fun url(path: String): String =
        if (path.startsWith("http", true)) path else origin + path

    suspend fun list(path: String): List<DavEntry> = withContext(Dispatchers.Default) {
        val body = """<?xml version="1.0" encoding="utf-8" ?>""" +
                """<D:propfind xmlns:D="DAV:"><D:prop>""" +
                """<D:displayname/><D:resourcetype/><D:getcontentlength/>""" +
                """</D:prop></D:propfind>"""

        val response = http.request(url(path)) {
            method = HttpMethod("PROPFIND")
            header("Depth", "1")
            header(HttpHeaders.ContentType, "application/xml; charset=utf-8")
            authHeader?.let { header(HttpHeaders.Authorization, it) }
            setBody(body)
        }
        val status = response.status.value
        val xml = response.bodyAsText()

        if (status !in 200..299) {
            AppLog.error("WebDAV", "PROPFIND $path -> HTTP $status")
            throw IllegalStateException("WebDAV 请求失败：HTTP $status")
        }
        // 不是 multistatus 说明拿到的是登录页/网页/错误页，而不是目录列表，
        // 直接报出来，避免“无报错但空列表”这种无法排查的状态
        if (!xml.contains("multistatus", ignoreCase = true)) {
            AppLog.error("WebDAV", "非 WebDAV 响应 HTTP $status：$path")
            throw IllegalStateException("服务器返回的不是 WebDAV 目录（HTTP $status，${xml.length} 字节）")
        }
        println("[dav] PROPFIND ${url(path)} -> HTTP $status, ${xml.length} chars")
        val entries = parseMultiStatus(xml, path)
        AppLog.info("WebDAV", "PROPFIND $path -> ${entries.size} 项")
        println("[dav] parsed ${entries.size} entries")
        if (entries.isEmpty()) {
            println("[dav] body head: ${xml.take(600).replace('\n', ' ')}")
        } else {
            entries.take(5).forEach { println("[dav] entry: ${it.name} dir=${it.isDir} href=${it.href}") }
        }
        entries
    }
}

/**
 * 解析 WebDAV 的 207 Multi-Status。
 * 抽成顶层函数是为了能直接做单元测试（desktopTest），避免再出现
 * “安卓能跑、iOS 空列表”这种平台分叉。
 */
internal fun parseMultiStatus(xml: String, requestedPath: String): List<DavEntry> {
    val responseRegex = Regex(
        "<(?:[\\w.-]+:)?response[^>]*>(.*?)</(?:[\\w.-]+:)?response>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    val collectionRegex = Regex("<(?:[\\w.-]+:)?collection\\s*/?>", RegexOption.IGNORE_CASE)

    return responseRegex.findAll(xml).mapNotNull { match ->
        val block = match.groupValues[1]
        val rawHref = tagText(block, "href")?.trim().orEmpty()
        if (rawHref.isEmpty()) return@mapNotNull null

        val href = davPathOf(rawHref)
        if (href.trimEnd('/') == requestedPath.trimEnd('/')) return@mapNotNull null

        val display = tagText(block, "displayname")?.trim().orEmpty()
        val name = display.ifBlank {
            percentDecode(href.trimEnd('/').substringAfterLast('/'))
        }
        DavEntry(
            name = name,
            href = href,
            // 部分 WebDAV 服务端（如 AList/OpenList 的某些存储）不返回 <collection/>，
            // 但目录 href 一定以 "/" 结尾，用这个兜底
            isDir = collectionRegex.containsMatchIn(block) || href.endsWith("/"),
            size = tagText(block, "getcontentlength")?.trim()?.toLongOrNull() ?: 0L
        )
    }
        .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        .toList()
}

private fun tagText(xml: String, tag: String): String? {
    val regex = Regex(
        "<(?:[\\w.-]+:)?$tag[^>]*>(.*?)</(?:[\\w.-]+:)?$tag>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    return regex.find(xml)?.groupValues?.get(1)
}


/** 视频扩展名（与 Android 端保持一致） */
val VIDEO_EXTS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "ts", "m2ts",
    "webm", "mpg", "mpeg", "rmvb", "rm", "3gp", "m4v", "vob"
)

fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS

/** 把 http://host/dav/x 归一化成 /dav/x */
fun davPathOf(raw: String): String {
    val h = raw.trim()
    if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) return h
    val rest = h.substringAfter("://")
    val slash = rest.indexOf('/')
    return if (slash < 0) "/" else rest.substring(slash)
}

/** 百分号解码（Kotlin/Native 没有 java.net.URLDecoder） */
fun percentDecode(s: String): String {
    if (!s.contains('%')) return s
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.add(hex.toByte())
                i += 3
                continue
            }
        }
        for (b in c.toString().encodeToByteArray()) bytes.add(b)
        i++
    }
    return bytes.toByteArray().decodeToString()
}

/** 极简 Base64（Basic 认证用） */
fun base64(input: String): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val bytes = input.encodeToByteArray()
    val sb = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        sb.append(table[b0 shr 2])
        sb.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
        sb.append(if (i + 1 < bytes.size) table[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
        sb.append(if (i + 2 < bytes.size) table[b2 and 0x3F] else '=')
        i += 3
    }
    return sb.toString()
}
