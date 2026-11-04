package com.davplayer.app.ios.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsImageRenderer
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageBitmap(bytes: ByteArray, maxWidth: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null

    val data = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    } ?: return null
    val image = UIImage(data = data) ?: return null

    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null

    val scale = min(1.0, maxWidth / width)
    val targetWidth = width * scale
    val targetHeight = height * scale
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight))
    val resized = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }

    val png = UIImagePNGRepresentation(resized) ?: return null
    val length = png.length.toInt()
    if (length <= 0) return null
    val raw = png.bytes ?: return null
    val pngBytes = raw.reinterpret<ByteVar>().readBytes(length)

    return org.jetbrains.skia.Image.makeFromEncoded(pngBytes).toComposeImageBitmap()
}
