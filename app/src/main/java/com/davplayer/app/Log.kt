package com.davplayer.app

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response

/** 内存环形日志：保留最近 300 条请求/缓存日志 */
object AppLog {
    data class Entry(val timeMs: Long, val tag: String, val message: String)

    private const val MAX = 300
    private val buffer = ArrayDeque<Entry>()

    @Synchronized
    fun log(tag: String, message: String) {
        buffer.addLast(Entry(System.currentTimeMillis(), tag, message))
        while (buffer.size > MAX) buffer.removeFirst()
    }

    @Synchronized
    fun entries(): List<Entry> = buffer.toList().asReversed()

    @Synchronized
    fun clear() = buffer.clear()
}

/** OkHttp 拦截器：记录每个请求的方法/路径/状态码/耗时 */
class HttpLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val resp = chain.proceed(req)
            AppLog.log(
                "HTTP",
                "${req.method} ${req.url.encodedPath} → ${resp.code} (${SystemClock.elapsedRealtime() - t0}ms)"
            )
            resp
        } catch (e: Exception) {
            AppLog.log("HTTP", "${req.method} ${req.url.encodedPath} → ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
