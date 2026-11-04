package com.davplayer.app.ios

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.davplayer.app.ios.player.PlayerBridge
import com.davplayer.app.ios.player.PlayerPresenter

/**
 * 桌面预览入口：只用于在 Windows 上快速验证 Compose 界面与 WebDAV 逻辑。
 * 播放能力仍由 iOS 的 VLCKit 提供，这里只打印地址。
 */
fun main() = application {
    PlayerBridge.presenter = object : PlayerPresenter {
        override fun present(url: String, title: String, user: String, pass: String) {
            println("[desktop] play: $title")
            println("[desktop] url : $url")
            println("[desktop] user: ${user.ifBlank { "<anonymous>" }}")
        }

        override fun dismiss() {
            println("[desktop] dismiss player")
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "DavPlayer · Desktop Preview",
        state = rememberWindowState(width = 1100.dp, height = 760.dp)
    ) {
        App()
    }
}
