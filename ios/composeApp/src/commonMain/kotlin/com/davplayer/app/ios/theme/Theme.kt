package com.davplayer.app.ios.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 与 Android 版 PulseStream 配色对齐（不追求像素一致，方向一致即可） */
val Bg = Color(0xFF0F0F0F)
val SurfaceBg = Color(0xFF1A1A1A)
val SurfaceHigh = Color(0xFF232323)
val Brand = Color(0xFFFF0050)
val Muted = Color(0xFF8A8A8A)
val Hairline = Color(0xFF262626)
val Danger = Color(0xFFFF6B6B)
val TextMain = Color(0xFFEDEDED)

@Composable
fun DavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = Bg,
            onBackground = TextMain,
            surface = SurfaceBg,
            onSurface = TextMain,
            surfaceVariant = SurfaceBg,
            onSurfaceVariant = Muted,
            error = Danger,
            onError = Color.White
        ),
        content = content
    )
}
