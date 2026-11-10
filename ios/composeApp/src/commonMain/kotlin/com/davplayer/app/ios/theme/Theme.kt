package com.davplayer.app.ios.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class IosPalette(
    val bg: Color,
    val surfaceBg: Color,
    val surfaceHigh: Color,
    val brand: Color,
    val muted: Color,
    val hairline: Color,
    val danger: Color,
    val textMain: Color
)

val DarkPalette = IosPalette(
    bg = Color(0xFF0E1116),
    surfaceBg = Color(0xFF171B23),
    surfaceHigh = Color(0xFF252B36),
    brand = Color(0xFFA5B4FC),
    muted = Color(0xFF8B93A1),
    hairline = Color(0x14FFFFFF),
    danger = Color(0xFFFF6B6B),
    textMain = Color(0xFFF2F3F5)
)

val LightPalette = IosPalette(
    bg = Color(0xFFF7F8FA),
    surfaceBg = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F2F5),
    brand = Color(0xFF4F46E5),
    muted = Color(0xFF86909C),
    hairline = Color(0x0F000000),
    danger = Color(0xFFDC2626),
    textMain = Color(0xFF1D2129)
)

val LocalIosPalette = staticCompositionLocalOf { DarkPalette }

val Bg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.bg

val SurfaceBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.surfaceBg

val SurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.surfaceHigh

val Brand: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.brand

val Muted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.muted

val Hairline: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.hairline

val Danger: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.danger

val TextMain: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalIosPalette.current.textMain

@Composable
fun DavTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.brand,
            onPrimary = Color(0xFF11162A),
            background = palette.bg,
            onBackground = palette.textMain,
            surface = palette.surfaceBg,
            onSurface = palette.textMain,
            surfaceVariant = palette.surfaceBg,
            onSurfaceVariant = palette.muted,
            error = palette.danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.brand,
            onPrimary = Color.White,
            background = palette.bg,
            onBackground = palette.textMain,
            surface = palette.surfaceBg,
            onSurface = palette.textMain,
            surfaceVariant = palette.surfaceBg,
            onSurfaceVariant = palette.muted,
            error = palette.danger,
            onError = Color.White
        )
    }
    CompositionLocalProvider(LocalIosPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
