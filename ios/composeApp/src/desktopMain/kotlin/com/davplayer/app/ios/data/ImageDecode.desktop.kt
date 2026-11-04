package com.davplayer.app.ios.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray, maxWidth: Int): ImageBitmap? {
    val source = org.jetbrains.skia.Image.makeFromEncoded(bytes)
    return try {
        if (source.width <= maxWidth) {
            source.toComposeImageBitmap()
        } else {
            val ratio = maxWidth.toFloat() / source.width
            val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(maxWidth, targetHeight)
            try {
                surface.canvas.drawImageRect(
                    source,
                    org.jetbrains.skia.Rect.makeWH(
                        source.width.toFloat(),
                        source.height.toFloat()
                    ),
                    org.jetbrains.skia.Rect.makeWH(
                        maxWidth.toFloat(),
                        targetHeight.toFloat()
                    )
                )
                surface.makeImageSnapshot().toComposeImageBitmap()
            } finally {
                surface.close()
            }
        }
    } finally {
        source.close()
    }
}
