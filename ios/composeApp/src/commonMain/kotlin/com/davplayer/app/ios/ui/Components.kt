package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.DavEntry
import com.davplayer.app.ios.data.percentDecode
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh

/** 目录 / 文件行：左侧图标 + 名称 + 摘要 */
@Composable
fun DavRow(
    entry: DavEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (entry.isDir) SurfaceBg else SurfaceHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (entry.isDir) "D" else "V", fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) Brand else Color(0xFFEDEDED)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (entry.isDir) "文件夹" else formatSize(entry.size),
                fontSize = 11.sp,
                color = Muted
            )
        }
        if (entry.isDir) {
            Text("›", fontSize = 18.sp, color = Muted)
        }
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
