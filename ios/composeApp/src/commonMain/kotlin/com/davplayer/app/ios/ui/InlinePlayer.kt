package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.AppLog
import com.davplayer.app.ios.player.PlayerBridge
import com.davplayer.app.ios.player.PlayerFrame

/**
 * 右侧内嵌播放器占位。
 * iOS 上 Swift 会在同一位置叠加 VLC 视图；desktop 预览只显示占位说明。
 */
@Composable
fun InlinePlayerHost(
    url: String,
    title: String,
    user: String,
    pass: String,
    sourceId: String,
    path: String,
    startMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    var shown by remember(url) { mutableStateOf(false) }
    var frame by remember(url) { mutableStateOf<PlayerFrame?>(null) }

    Box(
        modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                val next = PlayerFrame(
                    x = (rect.left / density).toDouble(),
                    y = (rect.top / density).toDouble(),
                    width = (rect.width / density).toDouble(),
                    height = (rect.height / density).toDouble()
                )
                frame = next
                if (shown) {
                    PlayerBridge.inlinePresenter?.updateFrame(next)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (PlayerBridge.inlinePresenter == null) {
            Text(
                "桌面预览不支持内嵌播放\n请用 iPad 验证 VLCKit",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
        }
    }

    LaunchedEffect(url, frame) {
        val current = frame ?: return@LaunchedEffect
        if (!shown) {
            PlayerBridge.registerSession(url, sourceId, path, title)
            AppLog.info("Player", "内嵌播放：$title @ ${startMs / 1000}s")
            PlayerBridge.inlinePresenter?.show(url, title, user, pass, startMs, sourceId, path, current)
            shown = true
        }
    }

    DisposableEffect(url) {
        onDispose {
            PlayerBridge.inlinePresenter?.hide()
        }
    }
}
