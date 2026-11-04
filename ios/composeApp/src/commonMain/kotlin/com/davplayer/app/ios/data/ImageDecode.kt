package com.davplayer.app.ios.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 按最大宽度解码图片。
 * iOS 用原生 ImageIO 先解码/缩放，再转 PNG 给 Skia；
 * 避免 Skia 自带 codec 在部分 WebP/CMYK/异常 JPEG 上损坏堆内存。
 */
expect fun decodeImageBitmap(bytes: ByteArray, maxWidth: Int): ImageBitmap?
