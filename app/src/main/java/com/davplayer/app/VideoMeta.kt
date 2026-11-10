package com.davplayer.app

/**
 * 不依赖网盘接口的轻量视频标签解析。
 * 只显示文件名里明确存在的信息，解析不到就返回 null，避免把 2024 当成 4K。
 */
data class VideoMeta(
    val resolution: String? = null,
    val hdr: String? = null,
    val codec: String? = null,
    val source: String? = null,
    val audio: String? = null
) {
    val labels: List<String>
        get() = listOfNotNull(resolution, hdr, codec, source, audio)
}

fun parseVideoMeta(fileName: String): VideoMeta {
    val name = fileName.trim()
    val lower = name.lowercase()

    val resolution = when {
        Regex("""(?i)(^|[.\s_\-\[\(])(2160p|4k|uhd)(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "4K"
        Regex("""(?i)(^|[.\s_\-\[\(])(1080p|fhd)(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "1080P"
        Regex("""(?i)(^|[.\s_\-\[\(])(720p)(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "720P"
        Regex("""(?i)(^|[.\s_\-\[\(])(576p|480p)(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "SD"
        else -> null
    }

    val hdr = when {
        lower.contains("dolby vision") || lower.contains("dolby.vision") || lower.contains("dovi") -> "DV"
        Regex("""(?i)(^|[.\s_\-\[\(])hdr10\+?(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "HDR10"
        Regex("""(?i)(^|[.\s_\-\[\(])hdr(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "HDR"
        else -> null
    }

    val codec = when {
        Regex("""(?i)(x265|h[.\-]?265|hevc)""").containsMatchIn(name) -> "HEVC"
        Regex("""(?i)(x264|h[.\-]?264|avc)""").containsMatchIn(name) -> "H.264"
        Regex("""(?i)(^|[.\s_\-\[\(])av1(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "AV1"
        else -> null
    }

    val source = when {
        lower.contains("remux") -> "REMUX"
        lower.contains("blu-ray") || lower.contains("bluray") || lower.contains("bdrip") -> "BluRay"
        lower.contains("web-dl") || lower.contains("webdl") -> "WEB-DL"
        lower.contains("webrip") -> "WEBRip"
        lower.contains("hdtv") -> "HDTV"
        else -> null
    }

    val audio = when {
        lower.contains("dts-hd") || lower.contains("dts hd") -> "DTS-HD"
        lower.contains("truehd") -> "TrueHD"
        lower.contains("atmos") -> "Atmos"
        Regex("""(?i)(^|[.\s_\-\[\(])dts(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "DTS"
        lower.contains("ddp") || lower.contains("eac3") -> "DDP"
        Regex("""(?i)(^|[.\s_\-\[\(])aac(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "AAC"
        else -> null
    }

    return VideoMeta(resolution, hdr, codec, source, audio)
}
