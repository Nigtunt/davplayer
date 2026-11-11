package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.DavEntry
import com.davplayer.app.ios.data.formatDavModified
import com.davplayer.app.ios.data.isSubtitleFile
import com.davplayer.app.ios.data.percentDecode
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh
import com.davplayer.app.ios.theme.TextMain

/** 3.0 目录 / 文件行：白色微浮卡片 */
@Composable
fun DavRow(
    entry: DavEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isSubtitle = isSubtitleFile(entry.name)
    val meta = if (entry.isDir || isSubtitle) VideoMeta() else parseVideoMeta(entry.name)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand.copy(alpha = 0.08f) else SurfaceBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (isSubtitle) 42.dp else 46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (entry.isDir) Brand.copy(alpha = 0.10f) else SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when {
                    entry.isDir -> Icons.Default.Folder
                    isSubtitle -> Icons.Default.Subtitles
                    else -> Icons.Default.Movie
                },
                null,
                tint = if (entry.isDir || isSubtitle) Brand else Muted,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) Brand else TextMain
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (entry.isDir) {
                    Text("文件夹", fontSize = 10.sp, color = Muted)
                } else if (isSubtitle) {
                    Text(
                        entry.name.substringAfterLast('.', "").uppercase().ifBlank { "字幕" },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand
                    )
                    Text(formatSize(entry.size), fontSize = 10.sp, color = Muted)
                } else {
                    meta.labels.take(3).forEach { label ->
                        Text(
                            label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brand.copy(alpha = 0.10f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(formatSize(entry.size), fontSize = 10.sp, color = Muted)
                }
                formatDavModified(entry.lastModified)?.let {
                    Text(it, fontSize = 9.sp, color = Muted, maxLines = 1)
                }
            }
        }
        if (entry.isDir) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 3.0 两列图标卡片 */
@Composable
fun DavGridCard(
    entry: DavEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSubtitle = isSubtitleFile(entry.name)
    val meta = if (entry.isDir || isSubtitle) VideoMeta() else parseVideoMeta(entry.name)
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand.copy(alpha = 0.08f) else SurfaceBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (entry.isDir) Brand.copy(alpha = 0.10f) else SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        entry.isDir -> Icons.Default.Folder
                        isSubtitle -> Icons.Default.Subtitles
                        else -> Icons.Default.Movie
                    },
                    null,
                    tint = if (entry.isDir || isSubtitle) Brand else Muted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (entry.isDir) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            entry.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) Brand else TextMain
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                entry.isDir -> "文件夹"
                isSubtitle -> "${entry.name.substringAfterLast('.', "").uppercase()} · ${formatSize(entry.size)}"
                else -> buildString {
                    append(formatSize(entry.size))
                    meta.labels.firstOrNull()?.let { append(" · $it") }
                }
            },
            fontSize = 9.sp,
            color = Muted,
            maxLines = 1
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Brand)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("加载失败：$message", color = Danger, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(999.dp)) { Text("重试") }
        }
    }
}

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
        lower.contains("dolby vision") || lower.contains("dovi") -> "DV"
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
        lower.contains("ddp") || lower.contains("eac3") -> "DDP"
        Regex("""(?i)(^|[.\s_\-\[\(])aac(?=$|[.\s_\]\)\-])""").containsMatchIn(name) -> "AAC"
        else -> null
    }
    return VideoMeta(resolution, hdr, codec, source, audio)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "${bytes / (1L shl 30)} GB"
    bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
    bytes >= 1L shl 10 -> "${bytes / (1L shl 10)} KB"
    else -> "$bytes B"
}

/** 目录显示名：取路径最后一段并解码 */
fun decodeName(path: String): String {
    val trimmed = path.trimEnd('/')
    val last = if (trimmed.isEmpty()) "" else trimmed.substringAfterLast('/')
    return percentDecode(last).ifBlank { "/" }
}
