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
import com.davplayer.app.ios.data.PrivacyLock
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.DavTheme
import com.davplayer.app.ios.ui.BrowserScreen
import com.davplayer.app.ios.ui.CalculatorScreen
import com.davplayer.app.ios.ui.ConnectScreen
import com.davplayer.app.ios.ui.PrivacySettingsDialog

/**
 * 应用根。
 * 隐私模式开启时先显示伪装计算器，输入暗码按 = 才进入真正的界面。
 */
@Composable
fun App() {
    DavTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (PrivacyLock.locked) {
                CalculatorScreen(onUnlock = { PrivacyLock.locked = false })
            } else {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    var client by remember { mutableStateOf<DavClient?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val current = client

    if (current == null) {
        ConnectScreen(
            onConnect = { client = it },
            onOpenSettings = { showSettings = true }
        )
    } else {
        key(current) {
            BrowserScreen(
                client = current,
                onExit = { client = null },
                onOpenSettings = { showSettings = true }
            )
        }
    }

    if (showSettings) {
        PrivacySettingsDialog(onDismiss = { showSettings = false })
    }
}
