package com.davplayer.app.ios.data

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults

private const val KEY_PRIVACY = "privacyMode"

actual fun readPrivacyMode(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(KEY_PRIVACY)

actual fun writePrivacyMode(enabled: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(enabled, KEY_PRIVACY)
    // 关闭隐私模式时顺手解锁，避免刚关掉还要再输一次暗码
    if (!enabled) PrivacyLock.locked = false
}

actual fun readSetting(key: String): String? =
    NSUserDefaults.standardUserDefaults.stringForKey(key)

actual fun writeSetting(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, key)
}

actual fun nowMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
