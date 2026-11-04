package com.davplayer.app.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.davplayer.app.ios.data.AppLog
import com.davplayer.app.ios.data.PrivacyLock
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.DavTheme
import com.davplayer.app.ios.ui.AppShell
import com.davplayer.app.ios.ui.CalculatorScreen

/**
 * 应用根。
 * 隐私模式开启时先显示伪装计算器，输入暗码按 = 才进入真正的界面。
 */
@Composable
fun App() {
    LaunchedEffect(Unit) { AppLog.info("App", "应用启动") }
    DavTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (PrivacyLock.locked) {
                CalculatorScreen(onUnlock = { PrivacyLock.locked = false })
            } else {
                AppShell()
            }
        }
    }
}
