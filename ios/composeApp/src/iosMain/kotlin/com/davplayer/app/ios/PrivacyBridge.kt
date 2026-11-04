package com.davplayer.app.ios

import com.davplayer.app.ios.data.PrivacyLock
import com.davplayer.app.ios.data.readPrivacyMode
import com.davplayer.app.ios.player.PlayerBridge

/**
 * 由 Swift 在 scenePhase 变为 background 时调用（见 iOSApp.swift），
 * 实现"应用退到后台后重新上锁"。
 */
fun onAppBackgrounded() {
    // 内嵌播放器是覆盖在窗口上的，退后台必须先撤掉，避免盖住隐私锁
    PlayerBridge.hideInline()
    if (readPrivacyMode()) {
        PrivacyLock.locked = true
    }
}
