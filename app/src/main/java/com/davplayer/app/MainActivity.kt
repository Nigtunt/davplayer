package com.davplayer.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Browse("浏览", Icons.Default.Folder),
    Komga("漫画", Icons.Default.MenuBook),
    History("历史", Icons.Default.History),
    Settings("我的", Icons.Default.Settings)
}

fun startPlayer(
    ctx: Context,
    href: String,
    title: String,
    positionMs: Long = 0L,
    subtitleHref: String? = null
) {
    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_HREF, href)
        putExtra(PlayerActivity.EXTRA_TITLE, title)
        putExtra(PlayerActivity.EXTRA_POSITION, positionMs)
        putExtra(PlayerActivity.EXTRA_SUBTITLE_HREF, subtitleHref)
    })
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }

    // ---------- 隐私模式：启动先显示计算器，暗码解锁 ----------
    var locked by remember { mutableStateOf(settings.privacyMode) }
    PrivacyGate.locked = locked

    // 整个应用退到后台时重新上锁；应用内切换页面（如进播放器）不会触发
    DisposableEffect(Unit) {
        val app = ctx.applicationContext as? Application
        var started = 1
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0 && settings.privacyMode) {
                    locked = true
                    // 同步给其它 Activity（此时 MainActivity 已停止，等重组会太晚）
                    PrivacyGate.locked = true
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        app?.registerActivityLifecycleCallbacks(callbacks)
        onDispose { app?.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    if (locked) {
        DavThemeProvider(dark = true) {
            CalculatorScreen(onUnlock = {
                locked = false
                PrivacyGate.locked = false
            })
        }
        return
    }

    var tab by remember { mutableStateOf(Tab.Browse) }
    var sourcesVersion by remember { mutableStateOf(0) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    val dark = resolveDark(themeMode)

    // 每个目录各自记住滚动位置：进入子目录再返回上级，停在原来的位置
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    // 浏览页要跳转到的目录（历史长按「跳转到视频目录」）
    var browseTarget by remember { mutableStateOf<String?>(null) }

    // 应用自更新（GitHub Releases）：设置页与启动自动检查共用一份状态
    val update = remember { UpdateState() }
    val currentVersion = remember { Updater.currentVersion(ctx) }
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val last = sp.getLong("lastUpdateCheck", 0L)
        if (System.currentTimeMillis() - last > 24 * 3600_000L) {
            sp.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
            update.check(ctx)
            // 「稍后」跳过过的版本不重复弹
            if (sp.getString("skippedUpdateVersion", null) == update.info?.version) {
                update.info = null
            }
        }
    }

    fun jumpToVideoDir(href: String) {
        val filePath = davPathOf(href)
        // 找到该文件所属的 WebDAV 源，必要时先切换源
        val match = settings.getSources().firstOrNull { s ->
            val root = runCatching { webDavRootPath(s.url) }.getOrNull()
            !root.isNullOrBlank() && filePath.startsWith(root)
        }
        if (match != null && match.id != settings.activeSourceId) {
            settings.activeSourceId = match.id
            sourcesVersion++
        }
        browseTarget = parentDirOf(filePath)
        tab = Tab.Browse
    }

    DavThemeProvider(dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { PulseBottomBar(tab) { tab = it } }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Browse -> BrowseScreen(
                        settings = settings,
                        sourcesVersion = sourcesVersion,
                        listStates = listStates,
                        targetPath = browseTarget,
                        onTargetConsumed = { browseTarget = null }
                    )
                    Tab.Komga -> KomgaScreen(
                        settings = settings,
                        dark = dark,
                        onToggleTheme = {
                            themeMode = if (dark) "light" else "dark"
                            settings.themeMode = themeMode
                        }
                    )
                    Tab.History -> HistoryScreen(onJumpToDir = { jumpToVideoDir(it) })
                    Tab.Settings -> SettingsScreen(
                        settings,
                        onChanged = { sourcesVersion++ },
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it },
                        update = update
                    )
                }
            }
        }

        // 启动时/设置页里发现新版本，统一在这里弹
        if (update.info != null) {
            UpdateDialog(
                state = update,
                currentVersion = currentVersion,
                onDismiss = {
                    ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putString("skippedUpdateVersion", update.info?.version).apply()
                    update.info = null
                }
            )
        }
    }
}

/** 底部导航：无硬分割线，白色浮层 + 选中主色胶囊 */
@Composable
private fun PulseBottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val extra = davExtra()
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp)
        ) {
            Tab.entries.forEach { t ->
                val selected = tab == t
                val color = if (selected) MaterialTheme.colorScheme.primary else extra.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(t.icon, t.label, tint = color, modifier = Modifier.size(19.dp))
                    }
                    Text(
                        t.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}

/** 筛选胶囊：白底细描边，选中主色 */
@Composable
fun PulseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val extra = davExtra()
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else extra.pill)
            .border(1.dp, if (selected) Color.Transparent else extra.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 浏览 ====================

private enum class BrowseFilter(val label: String) {
    All("全部"), Folder("文件夹"), Video("视频"), Subtitle("字幕")
}

private enum class BrowseSort(val label: String) {
    Name("名称"), Size("大小"), Modified("时间")
}

private fun sortDavEntries(list: List<DavEntry>, mode: String): List<DavEntry> = when {
    mode.equals(BrowseSort.Size.name, true) -> list.sortedByDescending { it.size }
    mode.equals(BrowseSort.Modified.name, true) -> list.sortedByDescending { it.lastModified }
    else -> list.sortedBy { it.name.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    settings: Settings,
    sourcesVersion: Int,
    listStates: MutableMap<String, LazyListState>,
    targetPath: String? = null,
    onTargetConsumed: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val historyStore = remember { HistoryStore(ctx) }

    val sources = remember(sourcesVersion) { settings.getSources() }
    var activeId by remember(sourcesVersion) { mutableStateOf(settings.activeSource()?.id) }
    val source = sources.firstOrNull { it.id == activeId } ?: sources.firstOrNull()

    if (source == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在「我的」中添加并保存 WebDAV 源", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val client = remember(source.id) { WebDavClient(source.url, source.user, source.pass) }

    val stack = remember(source.id) {
        // 重建从根目录到上次目录的完整路径链，保证「回上级/回主目录」可用
        mutableStateListOf<String>().apply {
            addAll(buildPathChain(client.rootPath, settings.lastPath))
        }
    }
    // 防御快速连点返回：即使状态列表短暂为空也不要 last() 崩溃
    val path = stack.lastOrNull() ?: client.rootPath
    LaunchedEffect(stack.isEmpty()) {
        if (stack.isEmpty()) stack.add(client.rootPath)
    }

    // 目录列表内存缓存：返回上级/重进目录直接用缓存，不重新请求
    val listCache = remember(source.id) { mutableMapOf<String, List<DavEntry>>() }

    // 目录内容按 path 记忆：切目录时立刻用该目录的缓存值（未缓存则为空），
    // 避免拿上一个目录的列表去布局新的滚动状态，把记录的位置挤掉
    var entries by remember(path) { mutableStateOf(listCache[path]) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(listCache[path] == null) }
    var refreshTick by remember(source.id) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 文件操作：左滑显示删除/移动，移动目标目录会记住上次位置
    var deleteTarget by remember { mutableStateOf<DavEntry?>(null) }
    var moveTarget by remember { mutableStateOf<DavEntry?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<DavEntry?>(null) }
    var infoTarget by remember { mutableStateOf<DavEntry?>(null) }
    var filter by remember { mutableStateOf(BrowseFilter.All) }
    var gridView by remember { mutableStateOf(settings.browseGridView) }
    var sortMode by remember { mutableStateOf(settings.browseSortMode) }
    val clipboard = LocalClipboardManager.current

    // 每个目录一份独立的滚动状态：进入子目录再返回上级，自动回到离开时的位置
    val listState = listStates.getOrPut(path) { LazyListState() }

    // 历史长按「跳转到视频目录」：重建路径链并切到目标目录
    LaunchedEffect(targetPath) {
        val target = targetPath ?: return@LaunchedEffect
        val chain = buildPathChain(client.rootPath, target)
        if (stack.toList() != chain) {
            stack.clear()
            stack.addAll(chain)
        }
        onTargetConsumed()
    }

    // 从播放器返回时单独刷新封面（播放中可能刚生成了封面）
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(path, refreshTick) {
        val cached = listCache[path]
        if (cached != null) {
            entries = cached
            error = null
            loading = false
            settings.lastPath = path
            return@LaunchedEffect
        }
        loading = true; error = null
        try {
            val list = client.list(path)
            listCache[path] = list
            entries = list
            settings.lastPath = path
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            entries = null
        }
        loading = false
    }

    // 视频行进度：整目录一次读入，按 href 匹配
    val historyMap = remember(resumeTick, path) { historyStore.list().associateBy { it.href } }

    BackHandler(enabled = stack.size > 1) {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    val allSupported = entries.orEmpty()
        .filter { it.isDir || isVideoFile(it.name) || isSubtitleFile(it.name) }
    val sortedFiles = sortDavEntries(allSupported.filterNot { it.isDir }, sortMode)
    val folders = sortDavEntries(allSupported.filter { it.isDir }, "name")
    val videos = sortedFiles.filter { isVideoFile(it.name) }
    val subtitles = sortedFiles.filter { isSubtitleFile(it.name) }
    val counts = mapOf(
        BrowseFilter.All to allSupported.size,
        BrowseFilter.Folder to folders.size,
        BrowseFilter.Video to videos.size,
        BrowseFilter.Subtitle to subtitles.size
    )
    val filesForFilter = when (filter) {
        BrowseFilter.Folder -> emptyList()
        BrowseFilter.Video -> videos
        BrowseFilter.Subtitle -> subtitles
        BrowseFilter.All -> sortedFiles
    }
    val status = when {
        loading -> "加载中" to Color(0xFFD97706)
        error != null -> "未连接" to Color(0xFFDC2626)
        else -> "已连接" to Color(0xFF16A34A)
    }
    val sortEnum = BrowseSort.entries.firstOrNull { it.name.equals(sortMode, true) } ?: BrowseSort.Name
    val openFolder: (DavEntry) -> Unit = { folder ->
        stack.add(if (folder.href.endsWith("/")) folder.href else folder.href + "/")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModernBrowseHeader(
            sourceName = source.name,
            sources = sources,
            onSwitchSource = { s ->
                settings.activeSourceId = s.id
                activeId = s.id
            },
            crumbs = davBreadcrumbs(client.rootPath, path),
            onCrumb = { crumb ->
                val chain = buildPathChain(client.rootPath, crumb.path)
                if (chain != stack.toList()) {
                    stack.clear()
                    stack.addAll(chain)
                }
            },
            canBack = stack.size > 1,
            onBack = { if (stack.size > 1) stack.removeAt(stack.size - 1) },
            filter = filter,
            onFilter = { filter = it },
            counts = counts,
            gridView = gridView,
            onToggleView = {
                gridView = !gridView
                settings.browseGridView = gridView
            },
            sortLabel = sortEnum.label,
            onSort = {
                val next = BrowseSort.entries[(sortEnum.ordinal + 1) % BrowseSort.entries.size]
                sortMode = next.name
                settings.browseSortMode = next.name
                Toast.makeText(ctx, "排序：${next.label}", Toast.LENGTH_SHORT).show()
            },
            onRefresh = { listCache.remove(path); refreshTick++ },
            statusText = status.first,
            statusColor = status.second
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshTick++ }) { Text("重试") }
                }
            }
            allSupported.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("空目录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            folders.isEmpty() && filesForFilter.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("没有匹配内容", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (filter == BrowseFilter.All || filter == BrowseFilter.Folder) {
                        item {
                            BrowseSectionTitle(
                                title = "文件夹",
                                count = folders.size,
                                trailing = if (filter == BrowseFilter.Folder) "名称" else null
                            )
                        }
                        if (gridView) {
                            items(folders.chunked(2), key = { row -> row.joinToString("|") { it.href } }) { row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { folder ->
                                        FolderGridCard(
                                            entry = folder,
                                            modifier = Modifier.weight(1f),
                                            onClick = { openFolder(folder) },
                                            onMore = { actionTarget = folder }
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(folders, key = { it.href }) { folder ->
                                FolderListCard(
                                    entry = folder,
                                    onClick = { openFolder(folder) },
                                    onMore = { actionTarget = folder }
                                )
                            }
                        }
                    }

                    if (filter != BrowseFilter.Folder && filesForFilter.isNotEmpty()) {
                        item {
                            BrowseSectionTitle(
                                title = when (filter) {
                                    BrowseFilter.Video -> "视频"
                                    BrowseFilter.Subtitle -> "字幕"
                                    else -> "文件"
                                },
                                count = filesForFilter.size
                            )
                        }
                        items(filesForFilter, key = { it.href }) { entry ->
                            SwipeableBrowseRow(
                                entry = entry,
                                history = historyMap[entry.href],
                                historyStore = historyStore,
                                resumeTick = resumeTick,
                                onClick = {
                                    if (isVideoFile(entry.name)) {
                                        scope.launch {
                                            val pos = HistoryStore(ctx).get(entry.href)?.positionMs ?: 0L
                                            val base = entry.name.substringBeforeLast('.').lowercase()
                                            val subtitle = subtitles.firstOrNull {
                                                it.name.substringBeforeLast('.').lowercase() == base
                                            }
                                            startPlayer(ctx, entry.href, entry.name, pos, subtitle?.href)
                                        }
                                    } else {
                                        actionTarget = entry
                                    }
                                },
                                onMore = { actionTarget = entry },
                                onDelete = { deleteTarget = entry },
                                onMove = { moveTarget = entry }
                            )
                        }
                    }
                }
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
                                    listCache.remove(path)
                                    entries = entries?.filterNot { it.href == target.href }
                                    refreshTick++
                                }
                                .onFailure {
                                    actionError = it.message ?: "删除失败"
                                    deleteTarget = null
                                }
                            actionBusy = false
                        }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    moveTarget?.let { target ->
        val initial = settings.lastMovePath(source.id)
            ?.takeIf { it.startsWith(client.rootPath) }
            ?: davParentPath(target.href)
        MoveFileDialog(
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
                            settings.setLastMovePath(source.id, dir)
                            moveTarget = null
                            listCache.remove(path)
                            entries = entries?.filterNot { it.href == target.href }
                            refreshTick++
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

    actionTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when {
                                target.isDir -> Icons.Default.Folder
                                isSubtitleFile(target.name) -> Icons.Default.Subtitles
                                else -> Icons.Default.PlayArrow
                            },
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            target.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                if (target.isDir) append("文件夹")
                                else if (isSubtitleFile(target.name)) append("字幕")
                                else append("视频")
                                if (!target.isDir && target.size > 0) append(" · ${formatSize(target.size)}")
                            },
                            fontSize = 10.sp,
                            color = davExtra().textMuted
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (target.isDir) {
                    SheetActionRow(Icons.Default.Folder, "打开") {
                        actionTarget = null
                        openFolder(target)
                    }
                } else if (isVideoFile(target.name)) {
                    SheetActionRow(Icons.Default.PlayArrow, "播放", MaterialTheme.colorScheme.primary) {
                        actionTarget = null
                        scope.launch {
                            val pos = historyStore.get(target.href)?.positionMs ?: 0L
                            startPlayer(ctx, target.href, target.name, pos)
                        }
                    }
                }
                SheetActionRow(Icons.Default.ContentCopy, "复制直链") {
                    clipboard.setText(AnnotatedString(client.fileUrl(target.href)))
                    Toast.makeText(ctx, "已复制直链", Toast.LENGTH_SHORT).show()
                    actionTarget = null
                }
                SheetActionRow(Icons.Default.Info, "文件信息") {
                    actionTarget = null
                    infoTarget = target
                }
                if (!target.isDir) {
                    SheetActionRow(Icons.Default.Edit, "移动") {
                        actionTarget = null
                        moveTarget = target
                    }
                    SheetActionRow(Icons.Default.Delete, "删除", MaterialTheme.colorScheme.error) {
                        actionTarget = null
                        deleteTarget = target
                    }
                }
            }
        }
    }

    infoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text("文件信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    InfoLine("名称", target.name)
                    InfoLine("路径", target.href)
                    InfoLine(
                        "类型",
                        when {
                            target.isDir -> "文件夹"
                            isSubtitleFile(target.name) -> "字幕"
                            else -> "视频"
                        }
                    )
                    if (!target.isDir) InfoLine("大小", formatSize(target.size))
                    formatDavModified(target.lastModified)?.let { InfoLine("修改时间", it) }
                }
            },
            confirmButton = { TextButton(onClick = { infoTarget = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            color = davExtra().textMuted,
            modifier = Modifier.width(56.dp)
        )
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ModernBrowseHeader(
    sourceName: String,
    sources: List<DavSource>,
    onSwitchSource: (DavSource) -> Unit,
    crumbs: List<DavBreadcrumb>,
    onCrumb: (DavBreadcrumb) -> Unit,
    canBack: Boolean,
    onBack: () -> Unit,
    filter: BrowseFilter,
    onFilter: (BrowseFilter) -> Unit,
    counts: Map<BrowseFilter, Int>,
    gridView: Boolean,
    onToggleView: () -> Unit,
    sortLabel: String,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
    statusText: String,
    statusColor: Color
) {
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val extra = davExtra()

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "上级目录",
                enabled = canBack,
                onClick = onBack
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(5.dp))
                    Box {
                        Text(
                            sourceName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { sourceMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = sourceMenuOpen,
                            onDismissRequest = { sourceMenuOpen = false }
                        ) {
                            sources.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(if (s.name == sourceName) "✓ ${s.name}" else s.name) },
                                    onClick = {
                                        sourceMenuOpen = false
                                        onSwitchSource(s)
                                    }
                                )
                            }
                        }
                    }
                    Text("  /  ", fontSize = 10.sp, color = extra.textMuted)
                    Text(
                        crumbs.lastOrNull()?.label ?: "根目录",
                        fontSize = 10.sp,
                        color = extra.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "媒体与文件库",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.10f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderIconButton(
                    icon = if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    description = if (gridView) "切换列表" else "切换图标",
                    onClick = onToggleView
                )
                HeaderIconButton(
                    icon = Icons.Default.Sort,
                    description = "排序：$sortLabel",
                    onClick = onSort
                )
                HeaderIconButton(
                    icon = Icons.Default.Refresh,
                    description = "刷新",
                    onClick = onRefresh
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { index, crumb ->
                val active = index == crumbs.lastIndex
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onCrumb(crumb) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index == 0) {
                        Icon(
                            Icons.Default.Home, null,
                            tint = if (active) MaterialTheme.colorScheme.primary else extra.textMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        crumb.label,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (index < crumbs.lastIndex) {
                    Text("›", fontSize = 11.sp, color = extra.textMuted)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            BrowseFilter.entries.forEach { item ->
                PulseChip("${item.label} ${counts[item] ?: 0}", filter == item) { onFilter(item) }
            }
        }
    }
}

@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape, ambientColor = Color(0x0A101828))
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else davExtra().textMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    HeaderCircleButton(icon, description, onClick = onClick)
}

@Composable
private fun BrowseSectionTitle(
    title: String,
    count: Int,
    trailing: String? = null
) {
    val extra = davExtra()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(7.dp))
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text("  $count", fontSize = 11.sp, color = extra.textMuted)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 10.sp, color = extra.textMuted)
        }
    }
}

/** 视频/字幕文件卡片：白色微浮卡片，信息按标题、标签、进度分层 */
@Composable
private fun BrowseRow(
    entry: DavEntry,
    history: HistoryItem?,
    historyStore: HistoryStore,
    resumeTick: Int,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val extra = davExtra()
    val isSubtitle = isSubtitleFile(entry.name)
    val meta = if (isVideoFile(entry.name)) parseVideoMeta(entry.name) else VideoMeta()
    val unsupported = isVideoFile(entry.name) && isUnsupportedVideo(entry.name)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (isSubtitle) 56.dp else 116.dp, if (isSubtitle) 56.dp else 66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSubtitle) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSubtitle) {
                Icon(
                    Icons.Default.Subtitles, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                FileCover(historyStore, entry.href, resumeTick, Modifier.fillMaxSize())
                meta.labels.firstOrNull()?.let { label ->
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val progress = history?.progress ?: 0f
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(DavTheme.BrandGradient)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f).heightIn(min = if (isSubtitle) 56.dp else 66.dp)) {
            Text(
                entry.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))

            if (isSubtitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name.substringAfterLast('.', "").uppercase().ifBlank { "字幕" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        " · ${formatSize(entry.size)}",
                        fontSize = 10.sp,
                        color = extra.textMuted
                    )
                    formatDavModified(entry.lastModified)?.let {
                        Text(" · $it", fontSize = 10.sp, color = extra.textMuted, maxLines = 1)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    meta.labels.take(3).forEach { label ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (unsupported) {
                        Text("需外部播放器", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatSize(entry.size),
                        fontSize = 10.sp,
                        color = extra.textMuted
                    )
                    formatDavModified(entry.lastModified)?.let {
                        Text("  ·  $it", fontSize = 10.sp, color = extra.textMuted, maxLines = 1)
                    }
                    history?.let {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "看到 ${formatMs(it.positionMs)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = DavTheme.Coral,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Default.MoreVert, "更多",
                tint = extra.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FolderListCard(
    entry: DavEntry,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val extra = davExtra()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "文件夹${formatDavModified(entry.lastModified)?.let { " · $it" } ?: ""}",
                fontSize = 10.sp,
                color = extra.textMuted,
                maxLines = 1
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.MoreVert, "更多", tint = extra.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FolderGridCard(
    entry: DavEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val extra = davExtra()
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMore, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, "更多", tint = extra.textMuted, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            entry.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatDavModified(entry.lastModified) ?: "文件夹",
            fontSize = 10.sp,
            color = extra.textMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun SwipeableBrowseRow(
    entry: DavEntry,
    history: HistoryItem?,
    historyStore: HistoryStore,
    resumeTick: Int,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    if (entry.isDir) {
        BrowseRow(entry, history, historyStore, resumeTick, onClick, onMore)
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
                .background(MaterialTheme.colorScheme.background)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwipeAction("删除", MaterialTheme.colorScheme.error) {
                settle(0f)
                onDelete()
            }
            SwipeAction("移动", MaterialTheme.colorScheme.primary) {
                settle(0f)
                onMove()
            }
        }

        Box(
            Modifier
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
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
            BrowseRow(
                entry = entry,
                history = history,
                historyStore = historyStore,
                resumeTick = resumeTick,
                onClick = {
                    if (dragOffset < -1f) settle(0f) else onClick()
                },
                onMore = onMore
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
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MoveFileDialog(
    client: WebDavClient,
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
    val crumbs = davBreadcrumbs(rootPath, path)
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
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    crumbs.forEachIndexed { index, crumb ->
                        Text(
                            crumb.label,
                            fontSize = 12.sp,
                            color = if (index == crumbs.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { path = crumb.path }
                                .padding(horizontal = 2.dp, vertical = 5.dp)
                        )
                        if (index < crumbs.lastIndex) {
                            Text(
                                ">",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    error != null -> Text("加载失败：$error", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    currentDirs == null -> Box(
                        Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    currentDirs.isEmpty() -> Text(
                        "没有子文件夹，可以直接移动到这里",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * 列表里的名称：默认折叠 [collapsedLines] 行，被截断时点标题即可展开看完整名称。
 * 点击「展开全名 / 收起」或直接点文字都能切换。
 */
@Composable
fun ExpandableName(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    lineHeight: TextUnit = 19.sp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var truncated by remember(text) { mutableStateOf(false) }
    val toggle = { expanded = !expanded }

    Column(modifier) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (result.hasVisualOverflow) truncated = true },
            modifier = if (truncated || expanded) Modifier.clickable(onClick = toggle) else Modifier
        )
        if (truncated || expanded) {
            Text(
                if (expanded) "收起" else "展开全名",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = toggle)
                    .padding(top = 2.dp)
            )
        }
    }
}

/** 文件列表中的封面缩略图：已有封面才显示，否则显示默认图标 */
@Composable
fun FileCover(store: HistoryStore, href: String, tick: Int = 0, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, href, tick) {
        value = withContext(Dispatchers.IO) {
            val f = store.coverFile(href)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }
    }
    val b = bitmap
    if (b != null) {
        Image(b, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Movie, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun decodeName(path: String): String = runCatching {
    java.net.URLDecoder.decode(path.trimEnd('/').substringAfterLast('/'), "UTF-8")
}.getOrDefault(path)

/** 由 WebDAV 根路径与目标目录重建完整路径链，保证「回上级/回主目录」可用 */
fun buildPathChain(root: String, target: String?): List<String> {
    val chain = mutableListOf(root)
    if (target.isNullOrBlank() || target == root || !target.startsWith(root)) return chain
    val rel = target.removePrefix(root).trim('/')
    if (rel.isEmpty()) return chain
    var cur = root
    rel.split('/').filter { it.isNotEmpty() }.forEach { seg ->
        cur += "$seg/"
        chain.add(cur)
    }
    return chain
}

private data class DavBreadcrumb(val label: String, val path: String)

/** 目录面包屑：显示中文、保留真实编码路径用于跳转 */
private fun davBreadcrumbs(root: String, current: String): List<DavBreadcrumb> =
    buildPathChain(root, current).mapIndexed { index, path ->
        DavBreadcrumb(
            label = if (index == 0) "根目录" else decodeName(path).ifBlank { path },
            path = path
        )
    }

// ==================== 历史 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onJumpToDir: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val settings = remember { Settings(ctx) }
    val extra = davExtra()

    var seg by remember { mutableStateOf(0) }   // 0=视频 1=漫画
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val items = remember(resumeTick) { store.list() }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // 漫画封面：加密缓存 + 认证（AES-GCM + Android KeyStore）
    val komgaClient = remember {
        if (settings.komgaUrl.isBlank()) null
        else KomgaClient(settings.komgaUrl, settings.komgaUser, settings.komgaPass)
    }
    val komgaLoader = remember { komgaClient?.let { buildKomgaImageLoader(ctx, it) } }

    // 漫画历史完全来自 Komga 服务端，不再读取本地漫画进度
    var comics by remember(komgaClient) { mutableStateOf<List<KomgaBook>?>(null) }
    var comicError by remember(komgaClient) { mutableStateOf<String?>(null) }
    var comicReload by remember(komgaClient) { mutableStateOf(0) }

    LaunchedEffect(seg, resumeTick, comicReload, komgaClient) {
        if (seg == 1 && komgaClient != null) {
            comics = null
            comicError = null
            try {
                comics = komgaClient.readBooks()
            } catch (e: Exception) {
                comicError = e.message ?: "加载失败"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
            Text(
                "历史",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.4).sp
            )
            Text(
                "视频播放与漫画阅读记录",
                fontSize = 10.sp,
                color = extra.textMuted
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(extra.pill)
                .padding(3.dp)
        ) {
            HistorySegment(
                label = "视频 ${items.size}",
                selected = seg == 0,
                modifier = Modifier.weight(1f)
            ) { seg = 0 }
            HistorySegment(
                label = comics?.let { "漫画 ${it.size}" } ?: "漫画",
                selected = seg == 1,
                modifier = Modifier.weight(1f)
            ) { seg = 1 }
        }

        if (seg == 0) {
            // ---------- 视频历史 ----------
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无视频播放历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.href }) { item ->
                    var menuOpen by remember(item.href) { mutableStateOf(false) }
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .combinedClickable(
                                    onClick = { startPlayer(ctx, item.href, item.title, item.positionMs) },
                                    onLongClick = { menuOpen = true }
                                )
                                .padding(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(132.dp, 74.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DavTheme.ThumbPlaceholder)
                                    .border(1.dp, extra.hairline, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                FileCover(store, item.href, resumeTick, Modifier.fillMaxSize())
                                // 右下角时长角标
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 8.dp, end = 5.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "${formatMs(item.positionMs)} / ${formatMs(item.durationMs)}",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                // 底部进度
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(item.progress)
                                            .fillMaxHeight()
                                            .background(DavTheme.BrandGradient)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(
                                Modifier.weight(1f).heightIn(min = 74.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                ExpandableName(item.title)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "已看 ${(item.progress * 100).toInt()}% · ${fmt.format(Date(item.updatedAt))}",
                                    fontSize = 11.sp,
                                    color = extra.textMuted
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { startPlayer(ctx, item.href, item.title, item.positionMs) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "继续",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // 长按菜单：跳转到视频所在目录 / 删除记录
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("跳转到视频目录")
                                        Text(
                                            decodeName(parentDirOf(item.href)).ifBlank { "/" },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, null) },
                                onClick = {
                                    menuOpen = false
                                    onJumpToDir(item.href)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除这条记录") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    store.remove(item.href)
                                    resumeTick++
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ---------- 漫画历史：服务端 Komga 进度 ----------
            when {
                komgaClient == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请先在「我的」里配置 Komga 服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                comicError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("漫画历史加载失败：$comicError", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { comicReload++ }) { Text("重试") }
                    }
                }
                comics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                comics!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无漫画阅读历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val client = komgaClient!!
                    val listContent: @Composable () -> Unit = {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(comics!!, key = { it.id }) { item ->
                                val time = item.progressTimestamp.take(16).replace('T', ' ')
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x0A101828))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            startReader(ctx, item.id, item.title, item.readPage, item.seriesTitle)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(62.dp, 90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DavTheme.ThumbPlaceholder)
                                            .border(1.dp, extra.hairline, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = SecureImage(client.bookThumbUrl(item.id)),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // 底部进度
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth(item.progress)
                                                    .fillMaxHeight()
                                                    .background(DavTheme.BrandGradient)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        ExpandableName(item.title)
                                        if (item.seriesTitle.isNotBlank()) {
                                            Text(
                                                item.seriesTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (time.isBlank()) "服务端进度：第 ${item.readPage} / ${item.pagesCount} 页"
                                            else "第 ${item.readPage} / ${item.pagesCount} 页 · $time",
                                            fontSize = 11.sp,
                                            color = extra.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (komgaLoader != null) {
                        CompositionLocalProvider(LocalImageLoader provides komgaLoader) { listContent() }
                    } else {
                        listContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 设置 ====================

/** 分组标签：小号全大写弱化文字 */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = davExtra().textMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** 设置卡片：白色微浮卡片 + 细描边 */
@Composable
private fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, davExtra().hairline)
    ) {
        Column(content = content)
    }
}

@Composable
private fun ProfileHeader(sourceName: String?, komgaConfigured: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x0A101828))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF818CF8)))),
            contentAlignment = Alignment.Center
        ) {
            Text("D", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("DavPlayer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                sourceName?.let { "WebDAV · $it" } ?: "还在配置媒体源",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (sourceName != null) Color(0xFF16A34A).copy(alpha = 0.10f)
                        else Color(0xFFD97706).copy(alpha = 0.10f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (sourceName != null) "已连接" else "未配置",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sourceName != null) Color(0xFF16A34A) else Color(0xFFD97706)
                )
            }
            if (komgaConfigured) {
                Spacer(Modifier.height(4.dp))
                Text("Komga 已连接", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 设置项图标盒：32dp 圆角 8 */
@Composable
private fun SIcon(emoji: String) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(davExtra().pill),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 15.sp)
    }
}

@Composable
private fun SRowDivider() {
    HorizontalDivider(
        color = davExtra().hairline,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 58.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onChanged: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    update: UpdateState
) {
    val ctx = LocalContext.current
    var sources by remember { mutableStateOf(settings.getSources()) }
    var activeId by remember { mutableStateOf(settings.activeSource()?.id) }

    // 编辑表单状态
    var showForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var fName by remember { mutableStateOf("") }
    var fUrl by remember { mutableStateOf("") }
    var fUser by remember { mutableStateOf("") }
    var fPass by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    fun openForm(src: DavSource?) {
        editingId = src?.id
        fName = src?.name ?: ""
        fUrl = src?.url ?: ""
        fUser = src?.user ?: ""
        fPass = src?.pass ?: ""
        formError = null
        showForm = true
    }

    var lpSpeed by remember { mutableStateOf(settings.longPressSpeed) }
    val speedOptions = listOf(1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    val extra = davExtra()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            ProfileHeader(
                sourceName = settings.activeSource()?.name,
                komgaConfigured = settings.komgaUrl.isNotBlank()
            )
        }

        // ---------- 外观主题 ----------
        item { GroupLabel("外观 · 主题") }
        item {
            SettingsBlock {
                Row(
                    Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SIcon("☀️")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("明暗主题", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("默认跟随系统，可手动切换浅色 / 深色", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (m, label) ->
                        PulseChip(label, themeMode == m) { settings.themeMode = m; onThemeChange(m) }
                    }
                }
            }
        }

        // ---------- WebDAV 源 ----------
        item { GroupLabel("WEBDAV 视频源") }
        item {
            SettingsBlock {
                if (sources.isEmpty() && !showForm) {
                    Text(
                        "还没有 WebDAV 源，点击下方「添加」创建。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(13.dp)
                    )
                }
                sources.forEachIndexed { i, s ->
                    if (i > 0) SRowDivider()
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = s.id == activeId,
                            onClick = {
                                settings.activeSourceId = s.id
                                activeId = s.id
                                onChanged()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        SIcon("🗄️")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                s.url,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { openForm(s) }) {
                            Icon(Icons.Default.Edit, "编辑", tint = extra.textMuted, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = {
                            sources = sources.filterNot { it.id == s.id }
                            settings.saveSources(sources)
                            onChanged()
                        }) {
                            Icon(Icons.Default.Delete, "删除", tint = extra.textMuted, modifier = Modifier.size(19.dp))
                        }
                    }
                }
                if (sources.isNotEmpty()) SRowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openForm(null) }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SIcon("＋")
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (showForm && editingId == null) "收起添加表单" else "添加 WebDAV 源",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showForm) {
            item {
                Spacer(Modifier.height(10.dp))
                SettingsBlock {
                    Column(
                        Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (editingId == null) "添加 WebDAV 源" else "编辑 WebDAV 源",
                            fontSize = 13.5.sp, fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = fName, onValueChange = { fName = it },
                            label = { Text("名称（如：家里 NAS）") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fUrl, onValueChange = { fUrl = it },
                            label = { Text("地址（如 http://192.168.1.10:5244/dav）") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fUser, onValueChange = { fUser = it },
                            label = { Text("用户名（可空）") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fPass, onValueChange = { fPass = it },
                            label = { Text("密码（可空）") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (formError != null) {
                            Text(formError!!, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (fUrl.isBlank() || !(fUrl.startsWith("http://") || fUrl.startsWith("https://"))) {
                                    formError = "请填写以 http:// 或 https:// 开头的地址"
                                    return@Button
                                }
                                val src = DavSource(
                                    id = editingId ?: java.util.UUID.randomUUID().toString(),
                                    name = fName.ifBlank { "WebDAV" },
                                    url = fUrl.trim(),
                                    user = fUser.trim(),
                                    pass = fPass
                                )
                                sources = sources.filterNot { it.id == src.id } + src
                                settings.saveSources(sources)
                                if (activeId == null || sources.size == 1) {
                                    settings.activeSourceId = src.id
                                    activeId = src.id
                                }
                                showForm = false
                                onChanged()
                            }, shape = RoundedCornerShape(999.dp)) { Text("保存") }
                            OutlinedButton(onClick = { showForm = false }, shape = RoundedCornerShape(999.dp)) { Text("取消") }
                        }
                    }
                }
            }
        }

        // ---------- Komga 服务器 ----------
        item { GroupLabel("KOMGA 漫画服务器") }
        item {
            var kUrl by remember { mutableStateOf(settings.komgaUrl) }
            var kUser by remember { mutableStateOf(settings.komgaUser) }
            var kPass by remember { mutableStateOf(settings.komgaPass) }
            var kMsg by remember { mutableStateOf<String?>(null) }
            var testing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            SettingsBlock {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("📚")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Komga 服务器", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("保存后可测试连接", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = kUrl, onValueChange = { kUrl = it },
                        label = { Text("地址（如 http://192.168.1.10:25600）") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kUser, onValueChange = { kUser = it },
                        label = { Text("邮箱") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kPass, onValueChange = { kPass = it },
                        label = { Text("密码") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    kMsg?.let { msg ->
                        Text(
                            msg,
                            color = if (msg.startsWith("✓")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (kUrl.isBlank() || !(kUrl.startsWith("http://") || kUrl.startsWith("https://"))) {
                                kMsg = "✗ 请填写以 http:// 或 https:// 开头的地址"
                                return@Button
                            }
                            settings.komgaUrl = kUrl.trim()
                            settings.komgaUser = kUser.trim()
                            settings.komgaPass = kPass
                            kMsg = "✓ 已保存"
                            onChanged()
                        }, shape = RoundedCornerShape(999.dp)) { Text("保存") }
                        OutlinedButton(
                            shape = RoundedCornerShape(999.dp),
                            enabled = !testing && kUrl.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    testing = true
                                    kMsg = null
                                    val ok = KomgaClient(kUrl, kUser, kPass).testConnection()
                                    kMsg = if (ok) "✓ 连接成功" else "✗ 连接失败，请检查地址和账号"
                                    testing = false
                                }
                            }
                        ) { Text(if (testing) "测试中…" else "测试连接") }
                    }
                }
            }
        }

        // ---------- 存储与缓存 ----------
        item { GroupLabel("存储 · 缓存") }
        item {
            var cacheStats by remember { mutableStateOf(SecureImageStore.stats(ctx)) }
            var clearMsg by remember { mutableStateOf<String?>(null) }
            var showLog by remember { mutableStateOf(false) }
            SettingsBlock {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("📦")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("加密图片缓存", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${cacheStats.first} 个文件 · ${formatSize(cacheStats.second)} · AES-256-GCM 加密",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp)) {
                    clearMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = RoundedCornerShape(999.dp), onClick = {
                            val n = SecureImageStore.clear(ctx)
                            AppLog.log("CACHE", "手动清空图片缓存（$n 个文件）")
                            cacheStats = SecureImageStore.stats(ctx)
                            clearMsg = "✓ 已清空 $n 个文件"
                        }) { Text("清空图片缓存") }
                        OutlinedButton(shape = RoundedCornerShape(999.dp), onClick = { showLog = true }) { Text("查看日志") }
                    }
                }
            }
            if (showLog) LogViewerDialog(onDismiss = { showLog = false })
        }

        // ---------- 播放 ----------
        item { GroupLabel("播放 · 解码") }
        item {
            SettingsBlock {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("⚡")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("长按倍速：${"%.1f".format(lpSpeed)}x", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("长按屏幕临时加速，松开恢复", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speedOptions.forEach { s ->
                        PulseChip("${"%.1f".format(s)}x", lpSpeed == s) {
                            lpSpeed = s; settings.longPressSpeed = s
                        }
                    }
                }
                SRowDivider()
                var decodeMode by remember { mutableStateOf(settings.decodeMode) }
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("🎞️")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("解码方式", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("部分视频硬解卡顿时切软件优先，重开视频生效", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("hw" to "硬件优先", "sw" to "软件优先").forEach { (m, label) ->
                        PulseChip(label, decodeMode == m) { decodeMode = m; settings.decodeMode = m }
                    }
                }
            }
        }

        // ---------- 隐私 ----------
        item { GroupLabel("隐私") }
        item {
            var privateMode by remember { mutableStateOf(settings.privacyMode) }
            SettingsBlock {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("🔒")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("隐私模式", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "启动时显示计算器，输入 19981212 后按 = 进入应用；退到后台会重新上锁",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = privateMode,
                        onCheckedChange = {
                            privateMode = it
                            settings.privacyMode = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // ---------- 关于 · 更新 ----------
        item { GroupLabel("关于 · 更新") }
        item {
            val scope = rememberCoroutineScope()
            val version = remember { Updater.currentVersion(ctx) }
            val hint = when {
                update.checking -> "正在检查…"
                update.info != null -> "发现新版本 ${update.info?.tag}"
                update.error != null -> update.error!!
                update.message != null -> update.message!!
                else -> "从 GitHub Releases 检查并下载新版本"
            }
            SettingsBlock {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon("🚀")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DavPlayer v$version", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            hint,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (update.info != null) MaterialTheme.colorScheme.primary
                            else if (update.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        enabled = !update.checking,
                        onClick = { scope.launch { update.check(ctx) } }
                    ) { Text(if (update.checking) "检查中" else "检查更新") }
                }
            }
        }
    }
}

/** 日志查看器：实时刷新显示最近的请求与缓存日志 */
@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf(AppLog.entries()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(800)
            entries = AppLog.entries()
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("运行日志", modifier = Modifier.weight(1f))
                TextButton(onClick = { AppLog.clear(); entries = emptyList() }) { Text("清空") }
            }
        },
        text = {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                    items(entries.size) { i ->
                        val e = entries[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                timeFmt.format(Date(e.timeMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                e.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (e.tag == "CACHE") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                e.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(thickness = 0.3.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
