package com.davplayer.app.ios.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 隐私模式暗码：与 Android 端保持一致 */
const val PRIVACY_CODE = "19981212"

/** 隐私模式开关（iOS 用 NSUserDefaults） */
expect fun readPrivacyMode(): Boolean

expect fun writePrivacyMode(enabled: Boolean)

/** 通用字符串配置（多源、历史等）：iOS NSUserDefaults / desktop Preferences */
expect fun readSetting(key: String): String?

expect fun writeSetting(key: String, value: String)

/** 当前时间戳（毫秒），用于历史排序 */
expect fun nowMillis(): Long

/**
 * 全局锁状态：启动时按开关初始化，应用退到后台时重新上锁。
 * 解锁后写入 false，其它界面无需关心。
 */
object PrivacyLock {
    var locked: Boolean by mutableStateOf(readPrivacyMode())
}
