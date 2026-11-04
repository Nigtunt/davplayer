package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.AppLog
import com.davplayer.app.ios.data.DavClient
import com.davplayer.app.ios.data.DavEntry
import com.davplayer.app.ios.data.DavSource
import com.davplayer.app.ios.data.SourceStore
import com.davplayer.app.ios.data.isVideoFile
import com.davplayer.app.ios.player.PlayerBridge
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh

/** 宽度达到这个值就切成 iPad 双栏 */
private val WideBreakpoint = 840.dp

private val SubtitleExts = setOf("srt", "ass", "ssa", "vtt")

@Composable
fun BrowserScreen(
    source: DavSource,
    initialPath: String? = null,
    highlightHref: String? = null,
    onSwitchSource: (DavSource) -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val client = remember(source) { DavClient(source.url, source.user, source.pass) }
    var stack by remember(source) {
        val restored = initialPath
            ?: SourceStore.lastPath(source.id)?.takeIf { it.startsWith(client.rootPath) }
            ?: client.rootPath
        // 从历史/上次路径进入时，栈里要包含 root → 目标目录的完整层级，
        // 否则 stack.size == 1，返回上级按钮永远是灰的
        mutableStateOf(buildPathStack(client.rootPath, restored))
    }
    val scrollPositions = remember(source.id) { mutableMapOf<String, Int>() }
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DavEntry?>(null) }
    var playing by remember { mutableStateOf<DavEntry?>(null) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    val path = stack.last()
    val listState = remember(path) { LazyListState(scrollPositions[path] ?: 0) }

    LaunchedEffect(path) { SourceStore.setLastPath(source.id, path) }

    DisposableEffect(path) {
        onDispose { scrollPositions[path] = listState.firstVisibleItemIndex }
    }

    LaunchedEffect(path, reloadTick) {
        loading = true
        error = null
        try {
            // 和 Android 版一致：只显示文件夹和视频
            entries = client.list(path).filter { it.isDir || isVideoFile(it.name) }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            AppLog.error("WebDAV", error!!)
            entries = null
        }
        loading = false
    }

    // 从“继续观看”进入时，自动选中上次播放的文件
    LaunchedEffect(entries, highlightHref) {
        if (highlightHref != null && selected == null) {
            selected = entries?.firstOrNull { it.href == highlightHref }
        }
    }

    val onUp: () -> Unit = {
        if (stack.size > 1) {
            stack = stack.dropLast(1)
            selected = null
        }
    }
    val onRetry: () -> Unit = { reloadTick++ }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide = maxWidth >= WideBreakpoint

        val startPlay: (DavEntry) -> Unit = play@ { entry ->
            AppLog.info(
                "UI",
                "点击视频：${entry.name} wide=$wide presenter=${PlayerBridge.presenter != null} inline=${PlayerBridge.inlinePresenter != null}"
            )
            if (wide && PlayerBridge.inlinePresenter == null) {
                playError = "内嵌播放器未注册（Swift 侧未初始化）"
                return@play
            }
            if (!wide && PlayerBridge.presenter == null) {
                playError = "全屏播放器未注册（Swift 侧未初始化）"
                return@play
            }
            val saved = SourceStore.progressFor(source.id, entry.href)
            val startMs = saved?.positionMs ?: 0L
            // 保留上一次的进度和封面，播放器随后会持续回写
            SourceStore.record(
                sourceId = source.id,
                path = entry.href,
                name = entry.name,
                positionMs = startMs,
                durationMs = saved?.durationMs ?: 0L,
                coverPath = saved?.coverPath
            )
            if (wide) {
                // 横屏：右侧内嵌播放
                playing = entry
            } else {
                // 竖屏：和 Android 一样全屏播放
                PlayerBridge.present(
                    url = client.url(entry.href),
                    title = entry.name,
                    user = client.authUser,
                    pass = client.authPass,
                    startMs = startMs,
                    sourceId = source.id,
                    path = entry.href
                )
            }
        }

        // 目录点击：文件夹进入；视频直接播放（和 Android 行为一致）
        val openEntry: (DavEntry) -> Unit = { entry ->
            if (entry.isDir) {
                stack = stack + (if (entry.href.endsWith("/")) entry.href else entry.href + "/")
                selected = null
            } else {
                selected = entry
                startPlay(entry)
            }
        }

        if (wide) {
            // iPad / 横屏：左侧目录树固定 380，右侧详情/播放区
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(380.dp).fillMaxHeight().background(SurfaceBg)) {
                    SidebarHeader(
                        source = source,
                        sources = SourceStore.sources,
                        onSwitchSource = onSwitchSource,
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
                        onOpen = openEntry,
                        state = listState,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                if (playing != null) {
                    InlinePlayerPane(
                        entry = playing!!,
                        url = client.url(playing!!.href),
                        user = client.authUser,
                        pass = client.authPass,
                        sourceId = source.id,
                        startMs = SourceStore.progressFor(source.id, playing!!.href)?.positionMs ?: 0L,
                        onPickSubtitle = { showSubtitlePicker = true },
                        onClose = { playing = null },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    DetailPane(
                        entry = selected,
                        onPlay = { selected?.let(startPlay) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // 手机 / 窄窗口：单列
            Column(Modifier.fillMaxSize()) {
                PhoneTopBar(
                    source = source,
                    sources = SourceStore.sources,
                    onSwitchSource = onSwitchSource,
                    subtitle = path,
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
                    onOpen = openEntry,
                    state = listState,
                    modifier = Modifier.weight(1f)
                )
                selected?.let { BottomHint(it, onPlay = { startPlay(it) }) }
            }
        }
    }

    playError?.let { message ->
        AlertDialog(
            onDismissRequest = { playError = null },
            title = { Text("无法播放") },
            text = { Text(message, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { playError = null }) { Text("好") }
            }
        )
    }

    if (showSubtitlePicker && playing != null) {
        SubtitlePickerDialog(
            client = client,
            videoPath = playing!!.href,
            onDismiss = { showSubtitlePicker = false },
            onSelect = { entry ->
                PlayerBridge.setSubtitle(client.url(entry.href))
                showSubtitlePicker = false
            }
        )
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
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    when {
        loading -> LoadingState(modifier)
        error != null -> ErrorState(error, onRetry, modifier)
        entries.isNullOrEmpty() -> EmptyState("空目录", modifier)
        else -> LazyColumn(state = state, modifier = modifier) {
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
    source: DavSource,
    sources: List<DavSource>,
    onSwitchSource: (DavSource) -> Unit,
    pathText: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SourceTitle(source, sources, onSwitchSource, 17.sp)
            }
            TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 12.sp, color = Muted) }
            TextButton(onClick = onExit) { Text("源管理", fontSize = 12.sp, color = Muted) }
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
    source: DavSource,
    sources: List<DavSource>,
    onSwitchSource: (DavSource) -> Unit,
    subtitle: String,
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
        Column(Modifier.weight(1f)) {
            SourceTitle(source, sources, onSwitchSource, 15.sp)
            Text(
                subtitle,
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRefresh) { Text("刷新", fontSize = 12.sp, color = Muted) }
        TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 12.sp, color = Muted) }
        TextButton(onClick = onExit) { Text("源管理", fontSize = 12.sp, color = Muted) }
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
            ) { Text("视频", fontSize = 16.sp, color = Muted) }

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
private fun SubtitlePickerDialog(
    client: DavClient,
    videoPath: String,
    onDismiss: () -> Unit,
    onSelect: (DavEntry) -> Unit
) {
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val dir = remember(videoPath) {
        val parent = videoPath.trimEnd('/').substringBeforeLast('/', "")
        if (parent.isBlank()) "/" else "$parent/"
    }

    LaunchedEffect(dir) {
        runCatching { client.list(dir) }
            .onSuccess { list ->
                entries = list.filter { entry ->
                    !entry.isDir &&
                        entry.name.substringAfterLast('.', "").lowercase() in SubtitleExts
                }
            }
            .onFailure { error = it.message ?: "加载失败" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择外挂字幕") },
        text = {
            when {
                error != null -> Text("加载失败：$error", color = Danger, fontSize = 12.sp)
                entries == null -> Box(
                    Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Brand) }
                entries!!.isEmpty() -> Text(
                    "视频目录里没有 srt/ass/ssa/vtt 字幕",
                    color = Muted,
                    fontSize = 12.sp
                )
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(entries!!) { entry ->
                        Text(
                            entry.name,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SourceTitle(
    source: DavSource,
    sources: List<DavSource>,
    onSelect: (DavSource) -> Unit,
    fontSize: TextUnit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                source.name,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(" ▾", fontSize = 11.sp, color = Muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sources.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.name,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        if (item.id == source.id) Text("√", color = Brand)
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun InlinePlayerPane(
    entry: DavEntry,
    url: String,
    user: String,
    pass: String,
    sourceId: String,
    startMs: Long,
    onPickSubtitle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("内嵌播放 · 右上角可全屏", fontSize = 10.sp, color = Muted)
            }
            TextButton(onClick = onPickSubtitle) { Text("字幕", fontSize = 12.sp, color = Muted) }
            TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp, color = Muted) }
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        key(entry.href) {
            InlinePlayerHost(
                url = url,
                title = entry.name,
                user = user,
                pass = pass,
                sourceId = sourceId,
                path = entry.href,
                startMs = startMs,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
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
