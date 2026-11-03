package com.davplayer.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

fun startPlayer(ctx: Context, href: String, title: String, positionMs: Long = 0L) {
    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_HREF, href)
        putExtra(PlayerActivity.EXTRA_TITLE, title)
        putExtra(PlayerActivity.EXTRA_POSITION, positionMs)
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

/** 底部导航：细描边分隔 + 图标/文字双行，选中加粗加深 */
@Composable
private fun PulseBottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val extra = davExtra()
    Column {
        HorizontalDivider(color = extra.hairline, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            Tab.entries.forEach { t ->
                val selected = tab == t
                val color = if (selected) MaterialTheme.colorScheme.onSurface else extra.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, t.label, tint = color, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.height(2.dp))
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

/** YouTube 风格胶囊筛选：8dp 微方圆角，选中反色 */
@Composable
fun PulseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val extra = davExtra()
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) extra.pillActiveBg else extra.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) extra.pillActiveText else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== 浏览 ====================

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
    val extra = davExtra()

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
    val path = stack.last()

    // 目录列表内存缓存：返回上级/重进目录直接用缓存，不重新请求
    val listCache = remember(source.id) { mutableMapOf<String, List<DavEntry>>() }

    // 目录内容按 path 记忆：切目录时立刻用该目录的缓存值（未缓存则为空），
    // 避免拿上一个目录的列表去布局新的滚动状态，把记录的位置挤掉
    var entries by remember(path) { mutableStateOf(listCache[path]) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(listCache[path] == null) }
    var refreshTick by remember(source.id) { mutableStateOf(0) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.size - 1) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    decodeName(path).ifBlank { source.name },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                )
            },
            navigationIcon = {
                // 回上级目录
                IconButton(
                    onClick = { stack.removeAt(stack.size - 1) },
                    enabled = stack.size > 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "上级目录")
                }
            },
            actions = {
                // 回主目录
                IconButton(
                    onClick = {
                        while (stack.size > 1) stack.removeAt(stack.size - 1)
                    },
                    enabled = stack.size > 1
                ) {
                    Icon(Icons.Default.Home, "主目录")
                }
                // 切换源
                Box {
                    TextButton(onClick = { sourceMenuOpen = true }) {
                        Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        sources.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s.id == source.id) "✓ ${s.name}" else s.name) },
                                onClick = {
                                    settings.activeSourceId = s.id
                                    activeId = s.id
                                    sourceMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { listCache.remove(path); refreshTick++ }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        HorizontalDivider(color = extra.hairline, thickness = 1.dp)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshTick++ }) { Text("重试") }
                }
            }
            entries.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("空目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val visible = entries!!.filter { it.isDir || isVideoFile(it.name) }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(visible, key = { it.href }) { entry ->
                        BrowseRow(
                            entry = entry,
                            history = historyMap[entry.href],
                            historyStore = historyStore,
                            resumeTick = resumeTick,
                            onClick = {
                                if (entry.isDir) {
                                    stack.add(if (entry.href.endsWith("/")) entry.href else entry.href + "/")
                                } else {
                                    scope.launch {
                                        val pos = HistoryStore(ctx).get(entry.href)?.positionMs ?: 0L
                                        startPlayer(ctx, entry.href, entry.name, pos)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 紧凑小图列表行：左侧 16:9 缩略图（角标 + 进度条）+ 右侧高密度信息 */
@Composable
private fun BrowseRow(
    entry: DavEntry,
    history: HistoryItem?,
    historyStore: HistoryStore,
    resumeTick: Int,
    onClick: () -> Unit
) {
    val extra = davExtra()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 缩略图盒
        Box(
            Modifier
                .size(132.dp, 74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (entry.isDir) extra.pill else DavTheme.ThumbPlaceholder)
                .border(1.dp, extra.hairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (entry.isDir) {
                Icon(
                    Icons.Default.Folder, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                FileCover(historyStore, entry.href, resumeTick, Modifier.fillMaxSize())
                // 右下角大小角标
                if (entry.size > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            formatSize(entry.size),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                // 底部观看进度
                val progress = history?.progress ?: 0f
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
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
        // 信息列
        Column(
            Modifier.weight(1f).heightIn(min = 74.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ExpandableName(entry.name)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isDir) {
                    Text("文件夹", fontSize = 11.sp, color = extra.textMuted)
                } else {
                    val unsupported = isUnsupportedVideo(entry.name)
                    // 格式标签
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (unsupported) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                else extra.pill
                            )
                            .padding(horizontal = 5.dp)
                    ) {
                        Text(
                            entry.name.substringAfterLast('.', "").uppercase().ifBlank { "视频" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (unsupported) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unsupported) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "需外部播放器",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    } else {
                        history?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "看到 ${formatMs(it.positionMs)}",
                                fontSize = 11.sp,
                                color = extra.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
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

// ==================== 历史 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(onJumpToDir: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val comicStore = remember { ComicHistoryStore(ctx) }
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
    val comics = remember(resumeTick) { comicStore.list() }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // 漫画封面：加密缓存 + 认证（AES-GCM + Android KeyStore）
    val komgaClient = remember {
        if (settings.komgaUrl.isBlank()) null
        else KomgaClient(settings.komgaUrl, settings.komgaUser, settings.komgaPass)
    }
    val komgaLoader = remember { komgaClient?.let { buildKomgaImageLoader(ctx, it) } }

    Column(Modifier.fillMaxSize()) {
        // 胶囊筛选
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulseChip("视频", seg == 0) { seg = 0 }
            PulseChip("漫画", seg == 1) { seg = 1 }
        }
        HorizontalDivider(color = extra.hairline, thickness = 1.dp)

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
                                .combinedClickable(
                                    onClick = { startPlayer(ctx, item.href, item.title, item.positionMs) },
                                    onLongClick = { menuOpen = true }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            IconButton(onClick = { store.remove(item.href); resumeTick++ }) {
                                Icon(Icons.Default.Delete, "删除记录", tint = extra.textMuted, modifier = Modifier.size(20.dp))
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
            // ---------- 漫画历史 ----------
            if (comics.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无漫画阅读历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val listContent: @Composable () -> Unit = {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(comics, key = { it.bookId }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        startReader(ctx, item.bookId, item.title, item.page, item.seriesTitle)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(62.dp, 90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DavTheme.ThumbPlaceholder)
                                        .border(1.dp, extra.hairline, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (komgaLoader != null && komgaClient != null) {
                                        AsyncImage(
                                            model = SecureImage(komgaClient.bookThumbUrl(item.bookId)),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.MenuBook, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(24.dp))
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
                                        "读到第 ${item.page} / ${item.pagesCount} 页 · ${fmt.format(Date(item.updatedAt))}",
                                        fontSize = 11.sp,
                                        color = extra.textMuted
                                    )
                                }
                                IconButton(onClick = { comicStore.remove(item.bookId); resumeTick++ }) {
                                    Icon(Icons.Default.Delete, "删除记录", tint = extra.textMuted, modifier = Modifier.size(20.dp))
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

/** 设置卡片：14dp 圆角 + 浅底 + 细描边 */
@Composable
private fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, davExtra().hairline)
    ) {
        Column(content = content)
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
                        Text("YouTube 邃黑与 Ins 纯白无缝切换", fontSize = 11.sp,
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
