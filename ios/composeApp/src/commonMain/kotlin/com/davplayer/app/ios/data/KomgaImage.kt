package com.davplayer.app.ios.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceHigh

/** 内存图片缓存（封面/漫画页）；后续可换成加密磁盘缓存 */
private val imageCache = LinkedHashMap<String, ImageBitmap>()

private fun cachePut(url: String, bitmap: ImageBitmap) {
    // iPad 2019 内存紧张；Skia 的图片内存对 iOS 不可见，压低缓存上限
    if (imageCache.size >= 40) {
        imageCache.keys.firstOrNull()?.let { imageCache.remove(it) }
    }
    imageCache[url] = bitmap
}

object KomgaImages {
    private val http = HttpClient { expectSuccess = false }

    // 固定在主线程解码：Skia 在 iPad 上并发 decode 会产生堆损坏（见 2026-11-04 crash log）
    suspend fun load(
        url: String,
        auth: String,
        maxWidth: Int = 480
    ): ImageBitmap? = withContext(Dispatchers.Main) {
        if (ComicStore.safeMode) return@withContext null
        runCatching {
            val response = http.get(url) {
                header(HttpHeaders.Authorization, auth)
            }
            if (response.status.value !in 200..299) {
                AppLog.warn("Komga", "图片 HTTP ${response.status.value}：$url")
                return@runCatching null
            }
            decodeImageBitmap(response.bodyAsBytes(), maxWidth)
        }.onFailure {
            AppLog.warn("Komga", "图片加载失败：$url")
        }.getOrNull()
    }
}

@Composable
fun KomgaImage(
    url: String,
    auth: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxWidth: Int = 480
) {
    val cacheKey = "$maxWidth|$url"
    var bitmap by remember(cacheKey) { mutableStateOf(imageCache[cacheKey]) }
    var failed by remember(cacheKey) { mutableStateOf(false) }

    LaunchedEffect(url, auth, maxWidth) {
        if (bitmap == null) {
            val loaded = KomgaImages.load(url, auth, maxWidth)
            if (loaded != null) {
                cachePut(cacheKey, loaded)
                bitmap = loaded
            } else {
                failed = true
            }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(SurfaceHigh), contentAlignment = Alignment.Center) {
            if (failed) {
                Text("无图", fontSize = 10.sp, color = Muted)
            } else {
                CircularProgressIndicator(
                    color = Brand,
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(0.18f)
                )
            }
        }
    }
}
