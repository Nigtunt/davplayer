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

fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS

class WebDavClient(baseUrl: String, username: String, password: String) {

    private val base = baseUrl.trim().trimEnd('/')
    private val uri: URI = URI(base)
    private val origin: String = "${uri.scheme}://${uri.authority}"
    private val user = username.trim()
    private val pass = password

    /** 服务器上的 WebDAV 根路径（已编码），如 /dav/ */
    val rootPath: String = (uri.rawPath ?: "/").let { if (it.endsWith("/")) it else "$it/" }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun authHeaders(): Map<String, String> =
        if (user.isBlank()) emptyMap()
        else mapOf("Authorization" to Credentials.basic(user, pass))

    fun fileUrl(href: String): String =
        if (href.startsWith("http")) href else origin + href

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
                        val h = href.toString().trim()
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
