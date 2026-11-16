package com.davplayer.app

/** 播放器解码/输出模式，对应设置项和播放中切换菜单。 */
enum class VideoDecodeMode(val storage: String, val label: String) {
    SW("sw", "SW 软解"),
    HW_COPY("hwcopy", "HW copy"),
    HW_PLUS("hw", "HW+ 直出");

    val usesMediaCodec: Boolean
        get() = this != SW

    companion object {
        fun fromSetting(value: String?): VideoDecodeMode =
            entries.firstOrNull { it.storage == value } ?: HW_PLUS
    }
}
