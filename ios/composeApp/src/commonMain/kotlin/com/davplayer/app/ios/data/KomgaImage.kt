package com.davplayer.app.ios.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.davplayer.app.ios.theme.Brand
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
    suspend fun load(url: String, auth: String): ImageBitmap? = withContext(Dispatchers.Main) {
        runCatching {
            val response = http.get(url) {
                header(HttpHeaders.Authorization, auth)
            }
            if (response.status.value !in 200..299) {
                AppLog.warn("Komga", "图片 HTTP ${response.status.value}：$url")
                return@runCatching null
            }
            val bytes = response.bodyAsBytes()
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
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
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(imageCache[url]) }

    LaunchedEffect(url, auth) {
        if (bitmap == null) {
            val loaded = KomgaImages.load(url, auth)
            if (loaded != null) {
                cachePut(url, loaded)
                bitmap = loaded
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
            CircularProgressIndicator(
                color = Brand,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize(0.18f)
            )
        }
    }
}
