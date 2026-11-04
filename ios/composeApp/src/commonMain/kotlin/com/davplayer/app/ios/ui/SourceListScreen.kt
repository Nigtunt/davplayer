package com.davplayer.app.ios.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.DavSource
import com.davplayer.app.ios.data.PlayProgress
import com.davplayer.app.ios.data.SourceStore
import com.davplayer.app.ios.data.readFileBytes
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh

/** 源列表 + 继续观看 */
@Composable
fun SourceListScreen(
    sources: List<DavSource>,
    history: List<PlayProgress>,
    onOpen: (DavSource) -> Unit,
    onResume: (PlayProgress) -> Unit,
    onAdd: () -> Unit,
    onEdit: (DavSource) -> Unit,
    onDelete: (DavSource) -> Unit,
    onOpenSettings: () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<DavSource?>(null) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DavPlayer", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
                        Spacer(Modifier.height(4.dp))
                        Text("WebDAV 视频 · iPad", fontSize = 12.sp, color = Muted)
                    }
                    TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 12.sp, color = Muted) }
                }

                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    Text("继续观看", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.take(6).forEach { item ->
                            HistoryRow(
                                item = item,
                                sourceName = SourceStore.sourceById(item.sourceId)?.name.orEmpty(),
                                onClick = { onResume(item) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("我的源", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = onAdd) { Text("＋ 添加", fontSize = 13.sp, color = Brand) }
                }
                Spacer(Modifier.height(4.dp))

                if (sources.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("还没有 WebDAV 源", fontSize = 13.sp, color = Muted)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sources.forEach { source ->
                            SourceRow(
                                source = source,
                                onClick = { onOpen(source) },
                                onEdit = { onEdit(source) },
                                onDelete = { pendingDelete = source }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("添加 WebDAV 源") }
                Spacer(Modifier.height(10.dp))
                Text(
                    "免费签名 7 天有效，到期前需要重新安装",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(24.dp))
                footer()
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除源") },
            text = { Text("确定删除「${target.name}」及其播放记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SourceRow(
    source: DavSource,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(SurfaceHigh, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🌐", fontSize = 16.sp) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                source.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                source.url,
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onEdit) { Text("编辑", fontSize = 12.sp, color = Muted) }
        TextButton(onClick = onDelete) { Text("删除", fontSize = 12.sp, color = Color(0xFFB06A6A)) }
    }
}

@Composable
fun HistoryRow(item: PlayProgress, sourceName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryThumb(item)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                sourceName.ifBlank { "WebDAV" },
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(progressLabel(item), fontSize = 11.sp, color = Muted)
        }
    }
}

/** 视频预览图：有截帧封面用封面，否则用渐变占位 + 扩展名 + 进度条 */
@Composable
private fun HistoryThumb(item: PlayProgress) {
    val cover = remember(item.coverPath) {
        item.coverPath?.let { path ->
            readFileBytes(path)?.let { bytes ->
                runCatching {
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }
        }
    }

    Box(
        Modifier
            .width(120.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C2C38), Color(0xFF17171D))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("视频", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
            }
        }

        val ext = item.name.substringAfterLast('.', "").uppercase().take(5)
        if (ext.isNotBlank()) {
            Text(
                ext,
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        val fraction = if (item.fraction > 0f) item.fraction else 0.02f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(Brand)
            )
        }
    }
}

private fun progressLabel(item: PlayProgress): String = when {
    item.durationMs > 0L -> "${formatClock(item.positionMs)} / ${formatClock(item.durationMs)}"
    item.positionMs > 0L -> "上次看到 ${formatClock(item.positionMs)}"
    else -> "最近播放"
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${pad(m)}:${pad(s)}" else "$m:${pad(s)}"
}

private fun pad(v: Long): String = if (v < 10) "0$v" else v.toString()
