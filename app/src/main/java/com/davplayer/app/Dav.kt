package com.davplayer.app

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class DavEntry(
    val name: String,
    val href: String,
    val isDir: Boolean,
    val size: Long
)

val VIDEO_EXTS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "ts", "m2ts",
    "webm", "mpg", "mpeg", "rmvb", "rm", "3gp", "m4v", "vob"
)

/**
 * Media3/ExoPlayer 内置解封装器不支持的容器格式：
 * RealMedia（RM/RMVB）与 ASF/WMV 没有对应的 Extractor，只能交给外部播放器。
 */
val UNSUPPORTED_EXTS = setOf("rmvb", "rm", "wmv", "asf")

fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS

fun isUnsupportedVideo(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in UNSUPPORTED_EXTS

/** 把可能是完整 URL（http://host/dav/x）的 href 归一化成服务器路径（/dav/x） */
fun davPathOf(href: String): String {
    val h = href.trim()
    return if (h.startsWith("http://", true) || h.startsWith("https://", true)) {
        runCatching { URI(h).rawPath ?: h }.getOrDefault(h)
    } else h
}

/** 由 WebDAV 源地址推导服务器根路径（已编码），如 /dav/ */
fun webDavRootPath(url: String): String {
    val base = url.trim().trimEnd('/')
    val raw = runCatching { URI(base).rawPath }.getOrNull() ?: "/"
    return if (raw.endsWith("/")) raw else "$raw/"
}

class WebDavClient(baseUrl: String, username: String, password: String) {

    private val base = baseUrl.trim().trimEnd('/')
    private val uri: URI = URI(base)
    private val origin: String = "${uri.scheme}://${uri.authority}"
    private val user = username.trim()
    private val pass = password

    /** 服务器上的 WebDAV 根路径（已编码），如 /dav/ */
    val rootPath: String = webDavRootPath(baseUrl)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLogInterceptor())
        .build()

    fun authHeaders(): Map<String, String> =
        if (user.isBlank()) emptyMap()
        else mapOf("Authorization" to Credentials.basic(user, pass))

    fun fileUrl(href: String): String =
        if (href.startsWith("http")) href else origin + href

    /** 带账号密码的地址：交给外部播放器时无法附加认证头，只能把凭据拼进 URL */
    fun fileUrlWithCredentials(href: String): String {
        val full = fileUrl(href)
        if (user.isBlank()) return full
        return runCatching {
            val u = URI(full)
            val info = "${encodeUserInfo(user)}:${encodeUserInfo(pass)}"
            URI(u.scheme, info, u.host, u.port, u.path, u.query, u.fragment).toString()
        }.getOrDefault(full)
    }

    private fun encodeUserInfo(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun list(path: String): List<DavEntry> = withContext(Dispatchers.IO) {
        val xml = """<?xml version="1.0" encoding="utf-8" ?>""" +
                """<D:propfind xmlns:D="DAV:"><D:prop>""" +
                """<D:displayname/><D:resourcetype/><D:getcontentlength/>""" +
                """</D:prop></D:propfind>"""
        val reqBuilder = Request.Builder()
            .url(fileUrl(path))
            .method("PROPFIND", xml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
        authHeaders().forEach { (k, v) -> reqBuilder.header(k, v) }

        http.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code !in 200..299) {
                throw IOException("WebDAV 请求失败: HTTP ${resp.code} ${resp.message}")
            }
            val bodyStr = resp.body?.string() ?: return@use emptyList()
            parseMultiStatus(bodyStr, path)
        }
    }

    private fun parseMultiStatus(xml: String, requestedPath: String): List<DavEntry> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val result = mutableListOf<DavEntry>()
        var href = StringBuilder()
        var name = StringBuilder()
        var size = StringBuilder()
        var isDir = false
        var tag = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.substringAfter(':')
                    when (tag) {
                        "response" -> {
                            href = StringBuilder(); name = StringBuilder()
                            size = StringBuilder(); isDir = false
                        }
                        "collection" -> isDir = true
                    }
                }
                XmlPullParser.TEXT -> when (tag) {
                    "href" -> href.append(parser.text)
                    "displayname" -> name.append(parser.text)
                    "getcontentlength" -> size.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.substringAfter(':') == "response") {
                        // 有些服务端返回完整 URL，统一成服务器路径，方便与目录栈/路径链比较
                        val h = davPathOf(href.toString().trim())
                        if (h.isNotEmpty() && !samePath(h, requestedPath)) {
                            val display = name.toString().trim().ifBlank {
                                runCatching {
                                    URLDecoder.decode(h.trimEnd('/').substringAfterLast('/'), "UTF-8")
                                }.getOrDefault(h)
                            }
                            result.add(
                                DavEntry(
                                    name = display,
                                    href = h,
                                    isDir = isDir,
                                    size = size.toString().trim().toLongOrNull() ?: 0L
                                )
                            )
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        return result.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    private fun samePath(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')
}
