package com.davplayer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * PulseStream 风格双主题设计令牌：
 * - 深色：YouTube 邃黑 (#0F0F0F) + 珊瑚霓虹红点缀
 * - 浅色：Ins 纯净白 (#FFFFFF) + 同系红色点缀
 * - 品牌渐变：#FF0050 → #FF5B2E → #FF00A0
 */
object DavTheme {
    val Accent = Color(0xFFFF0050)
    val AccentOrange = Color(0xFFFF5B2E)
    val AccentPink = Color(0xFFFF00A0)
    val LiveRed = Color(0xFFFF0033)
    val VerifiedBlue = Color(0xFF3EA6FF)

    val BrandGradient: Brush =
        Brush.linearGradient(listOf(Accent, AccentOrange, AccentPink))

    /** 缩略图占位底色（双主题通用） */
    val ThumbPlaceholder = Color(0xFF1E1E24)

    val DarkColors = darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4A0A20),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFAAAAAA),
        onSecondary = Color(0xFF0F0F0F),
        background = Color(0xFF0F0F0F),
        onBackground = Color(0xFFF1F1F1),
        surface = Color(0xFF0F0F0F),
        onSurface = Color(0xFFF1F1F1),
        surfaceVariant = Color(0xFF1A1A1A),          // bg-elevated
        onSurfaceVariant = Color(0xFFAAAAAA),
        outline = Color(0x14FFFFFF),                 // 8% 白描边
        outlineVariant = Color(0x14FFFFFF),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF272727)     // bg-pill
    )

    val LightColors = lightColorScheme(
        primary = Accent,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E001D),
        secondary = Color(0xFF606060),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF0F0F0F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F0F0F),
        surfaceVariant = Color(0xFFF8F9FA),          // bg-elevated
        onSurfaceVariant = Color(0xFF606060),
        outline = Color(0x12000000),                 // 7% 黑描边
        outlineVariant = Color(0x12000000),
        surfaceContainer = Color(0xFFF8F9FA),
        surfaceContainerHigh = Color(0xFFF2F2F2)     // bg-pill
    )
}

/** Material3 色板之外的扩展令牌（胶囊底色 / 细描边 / 弱化文字 / 反色胶囊） */
data class DavExtraColors(
    val pill: Color,
    val pillHover: Color,
    val hairline: Color,
    val textMuted: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color,
    val glass: Color
)

val LocalDavExtra = staticCompositionLocalOf {
    DavExtraColors(
        pill = Color(0xFF272727),
        pillHover = Color(0xFF3F3F3F),
        hairline = Color(0x14FFFFFF),
        textMuted = Color(0xFF717171),
        pillActiveBg = Color(0xFFF1F1F1),
        pillActiveText = Color(0xFF0F0F0F),
        glass = Color(0xDB0F0F0F)
    )
}

val DavExtraDark = DavExtraColors(
    pill = Color(0xFF272727),
    pillHover = Color(0xFF3F3F3F),
    hairline = Color(0x14FFFFFF),
    textMuted = Color(0xFF717171),
    pillActiveBg = Color(0xFFF1F1F1),
    pillActiveText = Color(0xFF0F0F0F),
    glass = Color(0xDB0F0F0F)
)

val DavExtraLight = DavExtraColors(
    pill = Color(0xFFF2F2F2),
    pillHover = Color(0xFFE5E5E5),
    hairline = Color(0x12000000),
    textMuted = Color(0xFF909090),
    pillActiveBg = Color(0xFF0F0F0F),
    pillActiveText = Color(0xFFFFFFFF),
    glass = Color(0xE0FFFFFF)
)

/** 一次性提供 MaterialTheme 色板 + 扩展令牌 */
@Composable
fun DavThemeProvider(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDavExtra provides if (dark) DavExtraDark else DavExtraLight) {
        MaterialTheme(colorScheme = if (dark) DavTheme.DarkColors else DavTheme.LightColors) {
            content()
        }
    }
}

@Composable
fun davExtra(): DavExtraColors = LocalDavExtra.current

/** "system" | "light" | "dark" */
@Composable
fun resolveDark(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
