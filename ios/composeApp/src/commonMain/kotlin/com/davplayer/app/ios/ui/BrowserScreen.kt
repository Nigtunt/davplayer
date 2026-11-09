package com.davplayer.app.ios.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
import com.davplayer.app.ios.data.davParentPath
import com.davplayer.app.ios.data.davTargetPath
import com.davplayer.app.ios.data.isVideoFile
import com.davplayer.app.ios.data.percentDecode
import com.davplayer.app.ios.player.PlayerBridge
import com.davplayer.app.ios.player.SubtitleEntry
import com.davplayer.app.ios.player.SubtitleOption
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    onOpenSettings: () -> Unit,
    onHistoryConsumed: () -> Unit = {}
) {
    val client = remember(source) { DavClient(source.url, source.user, source.pass) }

    // 字幕浏览器点击时才列目录，不在播放前预扫描
    DisposableEffect(client) {
        PlayerBridge.directoryLister = { dir ->
            client.list(dir)
                .mapNotNull { entry ->
                    when {
                        entry.isDir -> SubtitleEntry(entry.name, entry.href, "", true)
                        entry.name.substringAfterLast('.', "").lowercase() in SubtitleExts ->
                            SubtitleEntry(entry.name, entry.href, client.url(entry.href), false)
                        else -> null
                    }
                }
                .sortedWith(compareBy({ !it.isDir }, { it.name }))
        }
        onDispose { PlayerBridge.directoryLister = null }
    }

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
    var playingSubtitles by remember { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playPending by remember { mutableStateOf(false) }
    var autoPlayedHref by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 文件操作：右滑显示删除/移动，移动目标目录会记住上次位置
    var deleteTarget by remember { mutableStateOf<DavEntry?>(null) }
    var moveTarget by remember { mutableStateOf<DavEntry?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playPending) {
        if (playPending) {
            kotlinx.coroutines.delay(6000)
            if (PlayerBridge.playerPresented) {
                playPending = false
            } else {
                playPending = false
                playError = "播放器没有响应。请到「我的 → 运行日志」查看具体原因"
            }
        }
    }

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
            if (PlayerBridge.inlinePresenter == null) {
                playError = "内嵌播放器未注册（Swift 侧未初始化）"
                return@play
            }
            PlayerBridge.resetPlayerPresented()
            // 现在统一用内嵌播放器，不能等全屏 presenter 的 playerPresented，
            // 否则 playPending 的遮罩会盖住文件列表 6 秒。
            playPending = false
            val saved = SourceStore.progressFor(source.id, entry.href)
            val startMs = saved?.positionMs ?: 0L
            AppLog.info(
                "UI",
                "点击视频：${entry.name} wide=$wide startMs=${startMs / 1000}s"
            )
            // 保留上一次的进度和封面，播放器随后会持续回写
            SourceStore.record(
                sourceId = source.id,
                path = entry.href,
                name = entry.name,
                positionMs = startMs,
                durationMs = saved?.durationMs ?: 0L,
                coverPath = saved?.coverPath
            )
            playingSubtitles = emptyList()
            playing = entry
        }

        // 从历史点进来时直接播放，使用 SourceStore 里的上次进度
        LaunchedEffect(entries, highlightHref) {
            val href = highlightHref ?: return@LaunchedEffect
            if (autoPlayedHref == href) return@LaunchedEffect
            val entry = entries?.firstOrNull { it.href == href } ?: return@LaunchedEffect
            if (entry.isDir) return@LaunchedEffect
            autoPlayedHref = href
            selected = entry
            startPlay(entry)
            // 消费掉这次历史请求，避免切 tab / BrowserScreen 重建后再次自动播放
            onHistoryConsumed()
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
                        onDelete = { deleteTarget = it },
                        onMove = { moveTarget = it },
                        rowBackground = SurfaceBg,
                        state = listState,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
                DetailPane(
                    entry = selected,
                    onPlay = { selected?.let(startPlay) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // 手机 / 窄窗口：单列目录，播放器以 overlay 盖在上面
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
                    onDelete = { deleteTarget = it },
                    onMove = { moveTarget = it },
                    rowBackground = Bg,
                    state = listState,
                    modifier = Modifier.weight(1f)
                )
                selected?.let { BottomHint(it, onPlay = { startPlay(it) }) }
            }
        }

        // 播放器只在这一处 compose：横屏覆盖右侧内容区，竖屏覆盖整个内容区。
        // 旋转时只改 padding，InlinePlayerHost 不会被销毁，VLC 继续播放。
        playing?.let { entry ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (wide) 381.dp else 0.dp)
            ) {
                InlinePlayerPane(
                    entry = entry,
                    url = client.url(entry.href),
                    user = client.authUser,
                    pass = client.authPass,
                    sourceId = source.id,
                    startMs = SourceStore.progressFor(source.id, entry.href)?.positionMs ?: 0L,
                    subtitles = playingSubtitles,
                    onClose = { playing = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!actionBusy) deleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定删除「${target.name}」？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = {
                        actionBusy = true
                        scope.launch {
                            runCatching { client.delete(target.href) }
                                .onSuccess {
                                    deleteTarget = null
                                    if (selected?.href == target.href) selected = null
                                    entries = entries?.filterNot { it.href == target.href }
                                    reloadTick++
                                }
                                .onFailure {
                                    actionError = it.message ?: "删除失败"
                                    deleteTarget = null
                                }
                            actionBusy = false
                        }
                    }
                ) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    moveTarget?.let { target ->
        val initial = SourceStore.lastMovePath(source.id)
            ?.takeIf { it.startsWith(client.rootPath) }
            ?: davParentPath(target.href)
        MoveDirectoryDialog(
            client = client,
            entry = target,
            initialPath = initial,
            rootPath = client.rootPath,
            busy = actionBusy,
            onDismiss = { if (!actionBusy) moveTarget = null },
            onPick = { dir ->
                actionBusy = true
                scope.launch {
                    runCatching { client.move(target.href, davTargetPath(dir, target)) }
                        .onSuccess {
                            SourceStore.setLastMovePath(source.id, dir)
                            moveTarget = null
                            if (selected?.href == target.href) selected = null
                            entries = entries?.filterNot { it.href == target.href }
                            reloadTick++
                        }
                        .onFailure {
                            actionError = it.message ?: "移动失败"
                            moveTarget = null
                        }
                    actionBusy = false
                }
            }
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text("好") } }
        )
    }

    if (playPending) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Brand)
                Spacer(Modifier.height(10.dp))
                Text("正在打开播放器…", color = Color.White, fontSize = 13.sp)
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

}

@Composable
private fun DirectoryList(
    entries: List<DavEntry>?,
    loading: Boolean,
    error: String?,
    selectedHref: String?,
    onRetry: () -> Unit,
    onOpen: (DavEntry) -> Unit,
    onDelete: (DavEntry) -> Unit,
    onMove: (DavEntry) -> Unit,
    rowBackground: Color,
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    when {
        loading -> LoadingState(modifier)
        error != null -> ErrorState(error, onRetry, modifier)
        entries.isNullOrEmpty() -> EmptyState("空目录", modifier)
        else -> LazyColumn(state = state, modifier = modifier) {
            items(entries.orEmpty(), key = { it.href }) { entry ->
                SwipeableDavRow(
                    entry = entry,
                    selected = entry.href == selectedHref,
                    rowBackground = rowBackground,
                    onClick = { onOpen(entry) },
                    onDelete = { onDelete(entry) },
                    onMove = { onMove(entry) }
                )
            }
        }
    }
}

@Composable
private fun SwipeableDavRow(
    entry: DavEntry,
    selected: Boolean,
    rowBackground: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    if (entry.isDir) {
        DavRow(entry, selected, onClick)
        return
    }

    val actionWidth = 152.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    var dragOffset by remember(entry.href) { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun settle(target: Float) {
        scope.launch {
            animate(dragOffset, target, animationSpec = tween(180)) { value, _ ->
                dragOffset = value
            }
        }
    }

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier
                .matchParentSize()
                .background(rowBackground)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwipeAction("删除", Danger) {
                settle(0f)
                onDelete()
            }
            SwipeAction("移动", Brand) {
                settle(0f)
                onMove()
            }
        }

        Box(
            Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(rowBackground)
                .pointerInput(entry.href) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            settle(if (dragOffset < -actionWidthPx / 2f) -actionWidthPx else 0f)
                        },
                        onDragCancel = { settle(0f) },
                        onHorizontalDrag = { change, dragAmount ->
                            // 只处理左滑；右滑关闭已展开的行时也允许回弹
                            if (dragOffset < 0f || dragAmount < 0f) {
                                change.consume()
                                dragOffset = (dragOffset + dragAmount)
                                    .coerceIn(-actionWidthPx, 0f)
                            }
                        }
                    )
                }
        ) {
            DavRow(
                entry = entry,
                selected = selected,
                onClick = {
                    if (dragOffset < -1f) settle(0f) else onClick()
                }
            )
        }
    }
}

@Composable
private fun SwipeAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .width(64.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .background(color, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MoveDirectoryDialog(
    client: DavClient,
    entry: DavEntry,
    initialPath: String,
    rootPath: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var path by remember(entry.href, initialPath) { mutableStateOf(initialPath) }
    var dirs by remember { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(path, reload) {
        dirs = null
        error = null
        runCatching { client.list(path).filter { it.isDir } }
            .onSuccess { dirs = it }
            .onFailure { error = it.message ?: "加载失败" }
    }

    val atRoot = path.trimEnd('/') == rootPath.trimEnd('/')
    val pathStack = buildPathStack(rootPath, path)
    val crumbs = pathStack.mapIndexed { index, item ->
        if (index == 0) "根目录"
        else percentDecode(item.trimEnd('/').substringAfterLast('/')).ifBlank { item }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动文件") },
        text = {
            Column {
                Text(
                    entry.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    crumbs.forEachIndexed { index, label ->
                        val targetPath = pathStack[index]
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (index == crumbs.lastIndex) Brand else Muted,
                            modifier = Modifier
                                .clickable { path = targetPath }
                                .padding(horizontal = 2.dp, vertical = 5.dp)
                        )
                        if (index < crumbs.lastIndex) {
                            Text(
                                ">",
                                fontSize = 12.sp,
                                color = Muted,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val parent = davParentPath(path)
                            path = if (parent.startsWith(rootPath)) parent else rootPath
                        },
                        enabled = !atRoot
                    ) {
                        Text("↑ 上级")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { reload++ }) { Text("刷新") }
                }
                val currentDirs = dirs
                when {
                    error != null -> Text("加载失败：$error", color = Danger, fontSize = 12.sp)
                    currentDirs == null -> Box(
                        Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Brand) }
                    currentDirs.isEmpty() -> Text(
                        "没有子文件夹，可以直接移动到这里",
                        fontSize = 12.sp,
                        color = Muted
                    )
                    else -> {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(currentDirs, key = { it.href }) { dir ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            path = if (dir.href.endsWith("/")) dir.href else dir.href + "/"
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("▸", color = Brand, fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        dir.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(if (path.endsWith("/")) path else "$path/") },
                enabled = !busy
            ) { Text(if (busy) "移动中…" else "移动到这里") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
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
    subtitles: List<SubtitleOption>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxHeight().background(Bg)) {
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
                subtitles = subtitles,
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


