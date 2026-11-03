package com.davplayer.app.ios

import com.davplayer.app.ios.data.PrivacyLock
import com.davplayer.app.ios.data.readPrivacyMode

/**
 * 由 Swift 在 scenePhase 变为 background 时调用（见 iOSApp.swift），
 * 实现"应用退到后台后重新上锁"。
 */
fun onAppBackgrounded() {
    if (readPrivacyMode()) {
        PrivacyLock.locked = true
    }
}
