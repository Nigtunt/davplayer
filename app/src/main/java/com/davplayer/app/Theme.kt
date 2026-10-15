package com.davplayer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 高级感配色：
 * - 深色：墨黑底 + 黄铜金点缀
 * - 浅色：暖纸白底 + 同系金色点缀
 */
object DavTheme {
    val Brass = Color(0xFFC9A45C)
    val BrassDark = Color(0xFF8C6D2F)

    val DarkColors = darkColorScheme(
        primary = Brass,
        onPrimary = Color(0xFF241A05),
        primaryContainer = Color(0xFF3A2E14),
        onPrimaryContainer = Color(0xFFF0DCA8),
        secondary = Color(0xFF9FA3B5),
        onSecondary = Color(0xFF16161E),
        background = Color(0xFF0C0C12),
        onBackground = Color(0xFFE8E6EF),
        surface = Color(0xFF14141C),
        onSurface = Color(0xFFE8E6EF),
        surfaceVariant = Color(0xFF1E1E28),
        onSurfaceVariant = Color(0xFFA8A6B8),
        outline = Color(0xFF3A3A48),
        surfaceContainer = Color(0xFF181822),
        surfaceContainerHigh = Color(0xFF1F1F2A)
    )

    val LightColors = lightColorScheme(
        primary = BrassDark,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF3E7C8),
        onPrimaryContainer = Color(0xFF3A2C0A),
        secondary = Color(0xFF6B6D7A),
        onSecondary = Color(0xFFFFFFFF),
        background = Color(0xFFFAF7F1),
        onBackground = Color(0xFF201D17),
        surface = Color(0xFFFDFBF7),
        onSurface = Color(0xFF201D17),
        surfaceVariant = Color(0xFFEFEBE1),
        onSurfaceVariant = Color(0xFF5C584C),
        outline = Color(0xFFD8D2C2),
        surfaceContainer = Color(0xFFF4F0E8),
        surfaceContainerHigh = Color(0xFFEDE8DD)
    )
}

/** "system" | "light" | "dark" */
@Composable
fun resolveDark(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun davColorScheme(dark: Boolean) = if (dark) DavTheme.DarkColors else DavTheme.LightColors
