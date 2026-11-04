package com.davplayer.app.ios

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

/** Swift 侧入口：MainViewControllerKt.MainViewController() */
fun MainViewController() = ComposeUIViewController(
    configure = {
        // 默认 FocusableAboveKeyboard 会把整个 Compose 视图向上平移
        // 聚焦输入框时表现为"整页往上跑"；DoNothing 保持页面位置，由表单自己 imePadding
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
) { App() }
