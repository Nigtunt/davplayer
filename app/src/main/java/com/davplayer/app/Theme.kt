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
 * DavPlayer 3.0 设计令牌：
 * - 浅色优先：暖灰画布 + 纯白卡片 + 靛蓝主色
 * - 珊瑚红只用于播放/阅读进度
 * - 深色保留同层级关系，不简单反相
 */
object DavTheme {
    val Accent = Color(0xFF4F46E5)
    val AccentOrange = Color(0xFFFF7A45)
    val AccentPink = Color(0xFFFF4D4F)
    val LiveRed = Color(0xFFFF4D4F)
    val VerifiedBlue = Color(0xFF3D8BFF)
    val Coral = Color(0xFFFF4D4F)
    val IndigoSoft = Color(0xFFEEF2FF)

    /** 进度/品牌渐变：珊瑚红，不再用粉紫渐变 */
    val BrandGradient: Brush =
        Brush.linearGradient(listOf(AccentPink, AccentOrange))

    /** 缩略图无图时的深色底（真实封面加载前使用） */
    val ThumbPlaceholder = Color(0xFF1E1E24)

    val DarkColors = darkColorScheme(
        primary = Color(0xFFA5B4FC),
        onPrimary = Color(0xFF11162A),
        primaryContainer = Color(0xFF2B3260),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = Color(0xFFA6ADBB),
        onSecondary = Color(0xFF11151D),
        background = Color(0xFF0E1116),
        onBackground = Color(0xFFF2F3F5),
        surface = Color(0xFF0E1116),
        onSurface = Color(0xFFF2F3F5),
        surfaceVariant = Color(0xFF171B23),
        onSurfaceVariant = Color(0xFFA6ADBB),
        outline = Color(0x1FFFFFFF),
        outlineVariant = Color(0x14FFFFFF),
        surfaceContainer = Color(0xFF1B2029),
        surfaceContainerHigh = Color(0xFF252B36)
    )

    val LightColors = lightColorScheme(
        primary = Accent,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = IndigoSoft,
        onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Color(0xFF4E5969),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF1D2129),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1D2129),
        surfaceVariant = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF4E5969),
        outline = Color(0x0F000000),
        outlineVariant = Color(0x0F000000),
        surfaceContainer = Color(0xFFF0F2F5),
        surfaceContainerHigh = Color(0xFFEAECEF)
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
        pill = Color(0xFF252B36),
        pillHover = Color(0xFF303746),
        hairline = Color(0x14FFFFFF),
        textMuted = Color(0xFF8B93A1),
        pillActiveBg = Color(0xFFA5B4FC),
        pillActiveText = Color(0xFF11162A),
        glass = Color(0xE60E1116)
    )
}

val DavExtraDark = DavExtraColors(
    pill = Color(0xFF252B36),
    pillHover = Color(0xFF303746),
    hairline = Color(0x14FFFFFF),
    textMuted = Color(0xFF8B93A1),
    pillActiveBg = Color(0xFFA5B4FC),
    pillActiveText = Color(0xFF11162A),
    glass = Color(0xE60E1116)
)

val DavExtraLight = DavExtraColors(
    pill = Color(0xFFF0F2F5),
    pillHover = Color(0xFFE5E8EE),
    hairline = Color(0x0F000000),
    textMuted = Color(0xFF86909C),
    pillActiveBg = Color(0xFF4F46E5),
    pillActiveText = Color(0xFFFFFFFF),
    glass = Color(0xF2FFFFFF)
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
