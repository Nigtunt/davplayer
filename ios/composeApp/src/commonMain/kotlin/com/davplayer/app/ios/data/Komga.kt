package com.davplayer.app.ios.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ---------- 数据模型（字段与 Android Komga.kt 对齐） ----------

@Serializable
data class KomgaLibrary(val id: String, val name: String = "")

@Serializable
data class KomgaSeriesMeta(val title: String = "", val summary: String = "")

@Serializable
data class KomgaSeries(
    val id: String,
    val libraryId: String = "",
    val name: String = "",
    val booksCount: Int = 0,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val metadata: KomgaSeriesMeta? = null
) {
    val title: String get() = metadata?.title?.takeIf { it.isNotBlank() } ?: name
    val summary: String get() = metadata?.summary.orEmpty()
    val readProgress: Float
        get() = if (booksCount > 0) booksReadCount.toFloat() / booksCount else 0f
}

@Serializable
data class KomgaBookMeta(val title: String = "")

@Serializable
data class KomgaBookMedia(@SerialName("pagesCount") val pagesCount: Int = 0)

@Serializable
data class KomgaReadProgress(val page: Int = 0, val completed: Boolean = false)

@Serializable
data class KomgaBook(
    val id: String,
    val seriesId: String = "",
    val seriesTitle: String = "",
    val name: String = "",
    val number: Int = 0,
    val sizeBytes: Long = 0,
    val metadata: KomgaBookMeta? = null,
    val media: KomgaBookMedia? = null,
    val readProgress: KomgaReadProgress? = null
) {
    val title: String get() = metadata?.title?.takeIf { it.isNotBlank() } ?: name
    val pagesCount: Int get() = media?.pagesCount ?: 0
    val readPage: Int get() = readProgress?.page ?: 0
    val readCompleted: Boolean get() = readProgress?.completed ?: false
    val inProgress: Boolean get() = readPage > 0 && !readCompleted
}

@Serializable
data class KomgaPage(
    val number: Int,
    val width: Int = 0,
    val height: Int = 0,
    val mediaType: String = ""
)

@Serializable
data class KomgaReadList(
    val id: String,
    val name: String = "",
    val bookIds: List<String> = emptyList(),
    val summary: String = ""
)

@Serializable
data class KomgaPaged<T>(
    val content: List<T> = emptyList(),
    val last: Boolean = true,
    val totalElements: Long = 0
)

// ---------- 客户端 ----------

class KomgaClient(baseUrl: String, email: String, pass: String) {

    val base: String = baseUrl.trim().trimEnd('/')
    val authHeader: String = basicAuthHeader(email, pass)

    private val http = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private suspend fun getJson(path: String): String {
        val response = http.get("$base$path") {
            header(HttpHeaders.Authorization, authHeader)
        }
        val status = response.status.value
        if (status !in 200..299) {
            AppLog.error("Komga", "GET $path -> HTTP $status")
            throw IllegalStateException("Komga 请求失败：HTTP $status")
        }
        AppLog.info("Komga", "GET $path -> HTTP $status")
        return response.bodyAsText()
    }

    suspend fun testConnection(): Boolean = runCatching { libraries() }.isSuccess

    suspend fun libraries(): List<KomgaLibrary> =
        json.decodeFromString(getJson("/api/v1/libraries"))

    suspend fun series(
        page: Int = 0,
        size: Int = 30,
        libraryId: String? = null,
        search: String? = null
    ): KomgaPaged<KomgaSeries> {
        val q = StringBuilder("/api/v1/series?page=$page&size=$size&sort=metadata.titleSort,asc")
        if (!libraryId.isNullOrBlank()) q.append("&library_id=").append(percentEncode(libraryId))
        if (!search.isNullOrBlank()) q.append("&search=").append(percentEncode(search))
        return json.decodeFromString(getJson(q.toString()))
    }

    suspend fun latestSeries(size: Int = 12): List<KomgaSeries> =
        json.decodeFromString<KomgaPaged<KomgaSeries>>(
            getJson("/api/v1/series/latest?page=0&size=$size")
        ).content

    suspend fun onDeck(size: Int = 12): List<KomgaBook> =
        parseBookPage(getJson("/api/v1/books/ondeck?page=0&size=$size")).content

    suspend fun seriesBooks(seriesId: String): List<KomgaBook> =
        parseBookPage(
            getJson("/api/v1/series/$seriesId/books?unpaged=true&sort=metadata.numberSort,asc")
        ).content

    suspend fun bookPages(bookId: String): List<KomgaPage> {
        val text = getJson("/api/v1/books/$bookId/pages").trimStart()
        return if (text.startsWith("[")) {
            json.decodeFromString(text)
        } else {
            json.decodeFromString<KomgaPaged<KomgaPage>>(text).content
        }
    }

    suspend fun markReadProgress(bookId: String, page: Int, completed: Boolean) {
        val body = buildJsonObject {
            put("page", page)
            put("completed", completed)
        }.toString()
        val response = http.patch("$base/api/v1/books/$bookId/read-progress") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            AppLog.error("Komga", "进度同步失败 HTTP ${response.status.value}")
            throw IllegalStateException("进度同步失败：HTTP ${response.status.value}")
        }
        AppLog.info("Komga", "进度同步：${bookId.take(6)}… 第 $page 页")
    }

    // ---------- 阅读列表 ----------

    suspend fun readLists(): List<KomgaReadList> =
        json.decodeFromString<KomgaPaged<KomgaReadList>>(
            getJson("/api/v1/readlists?unpaged=true")
        ).content

    suspend fun readListBooks(readListId: String): List<KomgaBook> =
        parseBookPage(
            getJson("/api/v1/readlists/$readListId/books?unpaged=true&sort=readlist.number,asc")
        ).content

    suspend fun createReadList(name: String): String {
        val body = buildJsonObject {
            put("name", name)
            put("bookIds", buildJsonArray { })
            put("ordered", false)
            put("summary", "")
        }.toString()
        val response = http.post("$base/api/v1/readlists") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            throw IllegalStateException("创建阅读列表失败：HTTP ${response.status.value}")
        }
        val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return obj["id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("创建阅读列表失败：响应缺少 id")
    }

    suspend fun updateReadListBooks(readListId: String, bookIds: List<String>) {
        val body = buildJsonObject {
            put("bookIds", buildJsonArray { bookIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }.toString()
        val response = http.patch("$base/api/v1/readlists/$readListId") {
            header(HttpHeaders.Authorization, authHeader)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            throw IllegalStateException("更新阅读列表失败：HTTP ${response.status.value}")
        }
    }

    suspend fun deleteReadList(readListId: String) {
        val response = http.delete("$base/api/v1/readlists/$readListId") {
            header(HttpHeaders.Authorization, authHeader)
        }
        if (response.status.value !in 200..299 && response.status.value != 204) {
            throw IllegalStateException("删除阅读列表失败：HTTP ${response.status.value}")
        }
    }

    // ---------- 图片地址 ----------

    fun seriesThumbUrl(id: String) = "$base/api/v1/series/$id/thumbnail"
    fun bookThumbUrl(id: String) = "$base/api/v1/books/$id/thumbnail"
    fun pageUrl(bookId: String, pageNumber: Int) = "$base/api/v1/books/$bookId/pages/$pageNumber"
    fun readListThumbUrl(id: String) = "$base/api/v1/readlists/$id/thumbnail"

    // ---------- 解析 ----------

    private fun parseBookPage(text: String): KomgaPaged<KomgaBook> {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[")) {
            KomgaPaged(content = json.decodeFromString(text), last = true)
        } else {
            json.decodeFromString(text)
        }
    }
}

/** Basic 认证头（Kotlin 公共代码没有 Base64） */
fun basicAuthHeader(user: String, pass: String): String {
    val bytes = "$user:$pass".encodeToByteArray()
    val out = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        val n = (b0 shl 16) or (b1 shl 8) or b2
        out.append(B64[(n shr 18) and 0x3F])
        out.append(B64[(n shr 12) and 0x3F])
        out.append(if (i + 1 < bytes.size) B64[(n shr 6) and 0x3F] else '=')
        out.append(if (i + 2 < bytes.size) B64[n and 0x3F] else '=')
        i += 3
    }
    return "Basic $out"
}

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
