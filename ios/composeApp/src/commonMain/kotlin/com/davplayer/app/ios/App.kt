package com.davplayer.app.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.davplayer.app.ios.data.DavClient
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.DavTheme
import com.davplayer.app.ios.ui.BrowserScreen
import com.davplayer.app.ios.ui.ConnectScreen

/**
 * 应用根：连接页 → 浏览页。
 * 换源时用 key(client) 重置浏览状态，避免把上一个源的目录栈带过来。
 */
@Composable
fun App() {
    DavTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var client by remember { mutableStateOf<DavClient?>(null) }
            val current = client
            if (current == null) {
                ConnectScreen(onConnect = { client = it })
            } else {
                key(current) {
                    BrowserScreen(client = current, onExit = { client = null })
                }
            }
        }
    }
}
