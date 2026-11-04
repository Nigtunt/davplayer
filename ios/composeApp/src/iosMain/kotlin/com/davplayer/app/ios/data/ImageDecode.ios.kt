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
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import kotlin.math.min

/**
 * iOS：先用 UIImage 解码 + 缩放到目标尺寸，再转成 BGRA 原始像素，
 * 最后用 Skia raster 只做像素存储，不让 Skia 再跑 PNG/WebP/JPEG codec。
 * 之前的 PNG 中转会在 iPad 2019 上直接把内存撑爆（mach_vm_allocate 失败）。
 */
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
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    val renderer = UIGraphicsImageRenderer(
        size = CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()),
        format = format
    )
    val resized = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()))
    }

    val cgImage = resized.CGImage ?: return null
    val pixelWidth = CGImageGetWidth(cgImage).toInt()
    val pixelHeight = CGImageGetHeight(cgImage).toInt()
    if (pixelWidth <= 0 || pixelHeight <= 0) return null

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    // kCGImageAlphaPremultipliedFirst = 2；Kotlin/Native 里 alpha 常量不是顶层值，
    // kCGBitmapByteOrder32Little 本身就是 UInt32，直接按位组合成 BGRA。
    val bitmapInfo = kCGBitmapByteOrder32Little or 2u
    val context = CGBitmapContextCreate(
        data = null,
        width = pixelWidth.toULong(),
        height = pixelHeight.toULong(),
        bitsPerComponent = 8uL,
        bytesPerRow = (pixelWidth * 4).toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo
    ) ?: run {
        CGColorSpaceRelease(colorSpace)
        return null
    }

    CGContextDrawImage(
        context,
        CGRectMake(0.0, 0.0, pixelWidth.toDouble(), pixelHeight.toDouble()),
        cgImage
    )
    val raw = CGBitmapContextGetData(context)
    if (raw == null) {
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
        return null
    }
    val pixels = raw.reinterpret<ByteVar>().readBytes(pixelWidth * pixelHeight * 4)
    CGContextRelease(context)
    CGColorSpaceRelease(colorSpace)

    val skiaImage = Image.makeRaster(
        ImageInfo.makeN32Premul(pixelWidth, pixelHeight),
        pixels,
        pixelWidth * 4
    )
    return try {
        skiaImage.toComposeImageBitmap()
    } finally {
        skiaImage.close()
    }
}
