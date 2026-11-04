package com.davplayer.app.ios.data

import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 校验 iOS 侧使用的 Skia raster API 签名与当前 Compose/Skia 版本一致。
 */
class ImageRasterTest {

    @Test
    fun canCreateImageFromRawPixels() {
        val image = Image.makeRaster(
            ImageInfo.makeN32Premul(2, 2),
            ByteArray(2 * 2 * 4),
            2 * 4
        )
        assertNotNull(image)
        image.close()
    }
}
