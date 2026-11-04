package com.davplayer.app.ios.data

import java.util.prefs.Preferences

private const val KEY_PRIVACY = "privacyMode"

private val prefs = Preferences.userRoot().node("com/davplayer/app")

actual fun readPrivacyMode(): Boolean = prefs.getBoolean(KEY_PRIVACY, false)

actual fun writePrivacyMode(enabled: Boolean) {
    prefs.putBoolean(KEY_PRIVACY, enabled)
    if (!enabled) PrivacyLock.locked = false
}

actual fun readSetting(key: String): String? = prefs.get(key, null)

actual fun writeSetting(key: String, value: String) {
    prefs.put(key, value)
}

actual fun nowMillis(): Long = System.currentTimeMillis()
