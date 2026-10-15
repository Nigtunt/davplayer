package com.davplayer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class KomgaLibrary(val id: String, val name: String)

data class KomgaSeries(
    val id: String,
    val libraryId: String,
    val title: String,
    val booksCount: Int,
    val booksReadCount: Int,
    val booksUnreadCount: Int,
    val summary: String
) {
    val readProgress: Float
        get() = if (booksCount > 0) booksReadCount.toFloat() / booksCount else 0f
}

data class KomgaBook(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val title: String,
    val number: Int,
    val pagesCount: Int,
    val readPage: Int,          // 0 = 未读（Komga 的 page 是 1-based）
    val readCompleted: Boolean,
    val sizeBytes: Long
) {
    val inProgress: Boolean get() = readPage > 0 && !readCompleted
}

data class KomgaPage(val number: Int, val width: Int, val height: Int, val mediaType: String)

data class KomgaReadList(
    val id: String,
    val name: String,
    val bookIds: List<String>,
    val summary: String
)

data class PagedResult<T>(val items: List<T>, val isLast: Boolean, val totalElements: Long)

class KomgaClient(baseUrl: String, email: String, pass: String) {

    val base: String = baseUrl.trim().trimEnd('/')
    private val authHeader: String = Credentials.basic(email, pass)

    /** 带 Basic 认证的 OkHttpClient（也用于 Coil 加载封面/页面图片） */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLogInterceptor())
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", authHeader).build())
        }
        .build()

    private suspend fun getJson(path: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$base$path").build()).execute().use { r ->
            if (r.code !in 200..299) throw IOException("Komga 请求失败: HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        runCatching { libraries(); true }.getOrDefault(false)
    }

    suspend fun libraries(): List<KomgaLibrary> {
        val arr = JSONArray(getJson("/api/v1/libraries"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KomgaLibrary(o.getString("id"), o.getString("name"))
        }
    }

    suspend fun series(
        page: Int = 0,
        size: Int = 30,
        libraryId: String? = null,
        search: String? = null
    ): PagedResult<KomgaSeries> {
        val q = StringBuilder("/api/v1/series?page=$page&size=$size&sort=metadata.titleSort,asc")
        if (!libraryId.isNullOrBlank()) q.append("&library_id=").append(libraryId)
        if (!search.isNullOrBlank()) q.append("&search=").append(URLEncoder.encode(search, "UTF-8"))
        return parseSeriesPage(getJson(q.toString()))
    }

    suspend fun latestSeries(size: Int = 12): List<KomgaSeries> =
        parseSeriesPage(getJson("/api/v1/series/latest?page=0&size=$size")).items

    suspend fun onDeck(size: Int = 12): List<KomgaBook> =
        parseBookPage(getJson("/api/v1/books/ondeck?page=0&size=$size")).items

    suspend fun seriesBooks(seriesId: String): List<KomgaBook> =
        parseBookPage(getJson("/api/v1/series/$seriesId/books?unpaged=true&sort=metadata.numberSort,asc")).items

    suspend fun bookPages(bookId: String): List<KomgaPage> {
        val arr = JSONArray(getJson("/api/v1/books/$bookId/pages"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KomgaPage(
                number = o.getInt("number"),
                width = o.optInt("width"),
                height = o.optInt("height"),
                mediaType = o.optString("mediaType")
            )
        }
    }

    suspend fun markReadProgress(bookId: String, page: Int, completed: Boolean) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("page", page)
                .put("completed", completed)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            http.newCall(
                Request.Builder()
                    .url("$base/api/v1/books/$bookId/read-progress")
                    .patch(body)
                    .build()
            ).execute().use { r ->
                if (r.code !in 200..299) throw IOException("进度同步失败: HTTP ${r.code}")
            }
        }

    fun seriesThumbUrl(id: String) = "$base/api/v1/series/$id/thumbnail"
    fun bookThumbUrl(id: String) = "$base/api/v1/books/$id/thumbnail"
    fun pageUrl(bookId: String, pageNumber: Int) = "$base/api/v1/books/$bookId/pages/$pageNumber"
    fun readListThumbUrl(id: String) = "$base/api/v1/readlists/$id/thumbnail"

    // ---------- 阅读列表 ----------

    suspend fun readLists(libraryId: String? = null): List<KomgaReadList> {
        val q = StringBuilder("/api/v1/readlists?unpaged=true")
        if (!libraryId.isNullOrBlank()) q.append("&library_id=").append(libraryId)
        val root = JSONObject(getJson(q.toString()))
        val content = root.getJSONArray("content")
        return (0 until content.length()).map { i ->
            val o = content.getJSONObject(i)
            val ids = o.getJSONArray("bookIds")
            KomgaReadList(
                id = o.getString("id"),
                name = o.getString("name"),
                bookIds = (0 until ids.length()).map { ids.getString(it) },
                summary = o.optString("summary")
            )
        }
    }

    suspend fun readListBooks(readListId: String): List<KomgaBook> =
        parseBookPage(getJson("/api/v1/readlists/$readListId/books?unpaged=true&sort=readlist.number,asc")).items

    /** 新建阅读列表，返回 id */
    suspend fun createReadList(name: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .put("bookIds", JSONArray())
            .put("ordered", false)
            .put("summary", "")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        http.newCall(Request.Builder().url("$base/api/v1/readlists").post(body).build())
            .execute().use { r ->
                if (r.code !in 200..299) throw IOException("创建失败: HTTP ${r.code}")
                JSONObject(r.body?.string() ?: "{}").getString("id")
            }
    }

    /** 更新阅读列表的书籍集合（全量替换 bookIds） */
    suspend fun updateReadListBooks(readListId: String, bookIds: List<String>) =
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            bookIds.forEach { arr.put(it) }
            val body = JSONObject().put("bookIds", arr).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            http.newCall(
                Request.Builder().url("$base/api/v1/readlists/$readListId").patch(body).build()
            ).execute().use { r ->
                if (r.code !in 200..299) throw IOException("更新阅读列表失败: HTTP ${r.code}")
            }
        }

    suspend fun deleteReadList(readListId: String) = withContext(Dispatchers.IO) {
        http.newCall(
            Request.Builder().url("$base/api/v1/readlists/$readListId").delete().build()
        ).execute().use { r ->
            if (r.code !in 200..299 && r.code != 204) throw IOException("删除失败: HTTP ${r.code}")
        }
    }

    // ---------- 解析 ----------

    private fun parseSeriesPage(json: String): PagedResult<KomgaSeries> {
        val root = JSONObject(json)
        val content = root.getJSONArray("content")
        val items = (0 until content.length()).map { i ->
            val o = content.getJSONObject(i)
            val meta = o.optJSONObject("metadata") ?: JSONObject()
            KomgaSeries(
                id = o.getString("id"),
                libraryId = o.optString("libraryId"),
                title = meta.optString("title").ifBlank { o.optString("name") },
                booksCount = o.optInt("booksCount"),
                booksReadCount = o.optInt("booksReadCount"),
                booksUnreadCount = o.optInt("booksUnreadCount"),
                summary = meta.optString("summary")
            )
        }
        return PagedResult(items, root.optBoolean("last", true), root.optLong("totalElements"))
    }

    private fun parseBookPage(json: String): PagedResult<KomgaBook> {
        val root = if (json.trimStart().startsWith("{")) JSONObject(json) else null
        val content: JSONArray = root?.getJSONArray("content") ?: JSONArray(json)
        val items = (0 until content.length()).map { i -> parseBook(content.getJSONObject(i)) }
        val isLast = root?.optBoolean("last", true) ?: true
        val total = root?.optLong("totalElements", items.size.toLong()) ?: items.size.toLong()
        return PagedResult(items, isLast, total)
    }

    private fun parseBook(o: JSONObject): KomgaBook {
        val meta = o.optJSONObject("metadata") ?: JSONObject()
        val media = o.optJSONObject("media") ?: JSONObject()
        val rp = o.optJSONObject("readProgress")
        return KomgaBook(
            id = o.getString("id"),
            seriesId = o.optString("seriesId"),
            seriesTitle = o.optString("seriesTitle"),
            title = meta.optString("title").ifBlank { o.optString("name") },
            number = o.optInt("number"),
            pagesCount = media.optInt("pagesCount"),
            readPage = rp?.optInt("page", 0) ?: 0,
            readCompleted = rp?.optBoolean("completed", false) ?: false,
            sizeBytes = o.optLong("sizeBytes")
        )
    }
}
