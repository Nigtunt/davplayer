package com.davplayer.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密缓存的图片模型。
 * refresh = false：列表展示，缓存优先，无缓存才联网；
 * refresh = true ：进入详情时强制联网一次并更新缓存。
 */
data class SecureImage(val url: String, val refresh: Boolean = false)

/**
 * 图片加密存储：AES-256-GCM，密钥由 Android KeyStore 托管（不可导出）。
 * 封面（thumbnail）存 filesDir 持久保留；漫画页图存 cacheDir 由系统按需清理。
 */
object SecureImageStore {

    private const val KEY_ALIAS = "komga_image_key"
    private const val IV_LEN = 12

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun fileFor(ctx: Context, url: String): File {
        val name = MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val dir = if ("/pages/" in url) File(ctx.cacheDir, "secure_pages")
        else File(ctx.filesDir, "secure_covers")
        return File(dir.apply { mkdirs() }, "$name.enc")
    }

    @Synchronized
    fun get(ctx: Context, url: String): ByteArray? {
        val f = fileFor(ctx, url)
        if (!f.exists()) return null
        return runCatching {
            val raw = f.readBytes()
            if (raw.size <= IV_LEN) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw, 0, IV_LEN))
            cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN)
        }.getOrNull()
    }

    @Synchronized
    fun put(ctx: Context, url: String, bytes: ByteArray) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val enc = cipher.doFinal(bytes)
            fileFor(ctx, url).writeBytes(cipher.iv + enc)
        }
    }
}

/** Coil Fetcher：所有 SecureImage 请求都经过加密缓存层 */
class SecureImageFetcher(
    private val ctx: Context,
    private val http: OkHttpClient,
    private val image: SecureImage
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        if (!image.refresh) {
            SecureImageStore.get(ctx, image.url)?.let {
                return@withContext toResult(it, DataSource.DISK)
            }
        }
        val bytes = http.newCall(Request.Builder().url(image.url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.bytes() ?: throw IOException("empty body")
        }
        SecureImageStore.put(ctx, image.url, bytes)
        toResult(bytes, DataSource.NETWORK)
    }

    private fun toResult(bytes: ByteArray, source: DataSource): SourceResult =
        SourceResult(
            source = ImageSource(bytes.inputStream().source().buffer(), ctx),
            mimeType = null,
            dataSource = source
        )

    class Factory(
        private val ctx: Context,
        private val http: OkHttpClient
    ) : Fetcher.Factory<SecureImage> {
        override fun create(data: SecureImage, options: Options, imageLoader: ImageLoader): Fetcher =
            SecureImageFetcher(ctx, http, data)
    }
}

/** 构建带加密缓存 + 认证的 Komga 图片加载器（统一入口） */
fun buildKomgaImageLoader(ctx: Context, client: KomgaClient): ImageLoader =
    ImageLoader.Builder(ctx)
        .components { add(SecureImageFetcher.Factory(ctx.applicationContext, client.http)) }
        .crossfade(true)
        .build()
