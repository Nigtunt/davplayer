package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.DavClient
import com.davplayer.app.ios.data.DavEntry
import com.davplayer.app.ios.data.isVideoFile
import com.davplayer.app.ios.player.PlayerBridge
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh

/** 宽度达到这个值就切成 iPad 双栏 */
private val WideBreakpoint = 840.dp

@Composable
fun BrowserScreen(client: DavClient, onExit: () -> Unit, onOpenSettings: () -> Unit) {
    var stack by remember { mutableStateOf(listOf(client.rootPath)) }
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DavEntry?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    val path = stack.last()

    LaunchedEffect(path, reloadTick) {
        loading = true
        error = null
        try {
            // 和 Android 版一致：只显示文件夹和视频
            entries = client.list(path).filter { it.isDir || isVideoFile(it.name) }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            entries = null
        }
        loading = false
    }

    val onOpen: (DavEntry) -> Unit = { entry ->
        if (entry.isDir) {
            stack = stack + (if (entry.href.endsWith("/")) entry.href else entry.href + "/")
            selected = null
        } else {
            selected = entry
        }
    }
    val onUp: () -> Unit = {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            selected = null
        }
    }
    val onRetry: () -> Unit = { reloadTick++ }
    val onPlay: (DavEntry) -> Unit = { entry ->
        // 交给 Swift + MobileVLCKit 播放，Kotlin 只负责传地址与认证信息
        PlayerBridge.present(
            url = client.url(entry.href),
            title = entry.name,
            user = client.authUser,
            pass = client.authPass
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide = maxWidth >= WideBreakpoint

        if (wide) {
            // iPad / 横屏：左侧目录树固定 380，右侧详情/播放区
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(380.dp).fillMaxHeight().background(SurfaceBg)) {
                    SidebarHeader(
                        title = decodeName(path),
                        pathText = path,
                        canGoUp = stack.size > 1,
                        onUp = onUp,
                        onRefresh = onRetry,
                        onExit = onExit,
                        onOpenSettings = onOpenSettings
                    )
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    DirectoryList(
                        entries = entries,
                        loading = loading,
                        error = error,
                        selectedHref = selected?.href,
                        onRetry = onRetry,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                DetailPane(
                    entry = selected,
                    onPlay = { selected?.let(onPlay) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // 手机 / 窄窗口：单列
            Column(Modifier.fillMaxSize()) {
                PhoneTopBar(
                    title = decodeName(path),
                    canGoUp = stack.size > 1,
                    onUp = onUp,
                    onRefresh = onRetry,
                    onExit = onExit,
                    onOpenSettings = onOpenSettings
                )
                HorizontalDivider(color = Hairline, thickness = 1.dp)
                DirectoryList(
                    entries = entries,
                    loading = loading,
                    error = error,
                    selectedHref = selected?.href,
                    onRetry = onRetry,
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f)
                )
                selected?.let { BottomHint(it, onPlay = { onPlay(it) }) }
            }
        }
    }
}

@Composable
private fun DirectoryList(
    entries: List<DavEntry>?,
    loading: Boolean,
    error: String?,
    selectedHref: String?,
    onRetry: () -> Unit,
    onOpen: (DavEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading -> LoadingState(modifier)
        error != null -> ErrorState(error, onRetry, modifier)
        entries.isNullOrEmpty() -> EmptyState("空目录", modifier)
        else -> LazyColumn(modifier) {
            items(entries.orEmpty(), key = { it.href }) { entry ->
                DavRow(
                    entry = entry,
                    selected = entry.href == selectedHref,
                    onClick = { onOpen(entry) }
                )
            }
        }
    }
}

@Composable
private fun SidebarHeader(
    title: String,
    pathText: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 12.sp, color = Muted) }
            TextButton(onClick = onExit) { Text("换源", fontSize = 12.sp, color = Muted) }
        }
        Text(
            pathText,
            fontSize = 11.sp,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onUp, enabled = canGoUp) {
                Text("← 上级", fontSize = 12.sp, color = if (canGoUp) Brand else Muted)
            }
            TextButton(onClick = onRefresh) { Text("刷新", fontSize = 12.sp, color = Muted) }
        }
    }
}

@Composable
private fun PhoneTopBar(
    title: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onUp, enabled = canGoUp) {
            Text("←", fontSize = 18.sp, color = if (canGoUp) Brand else Muted)
        }
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRefresh) { Text("刷新", fontSize = 12.sp, color = Muted) }
        TextButton(onClick = onOpenSettings) { Text("⚙", fontSize = 15.sp, color = Muted) }
        TextButton(onClick = onExit) { Text("换源", fontSize = 12.sp, color = Muted) }
    }
}

/** iPad 右侧详情区 */
@Composable
private fun DetailPane(entry: DavEntry?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (entry == null) {
            Text("从左侧选择视频", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("目录会显示在左边，文件详情显示在这里", color = Muted, fontSize = 12.sp)
        } else {
            Box(
                Modifier.size(120.dp).background(SurfaceHigh, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🎬", fontSize = 44.sp) }

            Spacer(Modifier.height(22.dp))
            Text(
                entry.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(formatSize(entry.size), fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(4.dp))
            Text(
                entry.href,
                fontSize = 11.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(26.dp))
            Button(onClick = onPlay, shape = RoundedCornerShape(999.dp)) {
                Text("播放")
            }
        }
    }
}

@Composable
private fun BottomHint(entry: DavEntry, onPlay: () -> Unit) {
    HorizontalDivider(color = Hairline, thickness = 1.dp)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            entry.name,
            fontSize = 12.sp,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPlay) {
            Text("播放", fontSize = 13.sp, color = Brand)
        }
    }
}
