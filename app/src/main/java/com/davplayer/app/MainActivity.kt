package com.davplayer.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
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

private enum class Tab(val label: String) {
    Browse("浏览"), Komga("漫画"), History("历史"), Settings("设置")
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
    var tab by remember { mutableStateOf(Tab.Browse) }
    var sourcesVersion by remember { mutableStateOf(0) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    val dark = resolveDark(themeMode)

    MaterialTheme(colorScheme = davColorScheme(dark)) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Browse,
                        onClick = { tab = Tab.Browse },
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text(Tab.Browse.label) })
                    NavigationBarItem(
                        selected = tab == Tab.Komga,
                        onClick = { tab = Tab.Komga },
                        icon = { Icon(Icons.Default.MenuBook, null) },
                        label = { Text(Tab.Komga.label) })
                    NavigationBarItem(
                        selected = tab == Tab.History,
                        onClick = { tab = Tab.History },
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text(Tab.History.label) })
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text(Tab.Settings.label) })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Browse -> BrowseScreen(settings, sourcesVersion)
                    Tab.Komga -> KomgaScreen(
                        settings = settings,
                        dark = dark,
                        onToggleTheme = {
                            themeMode = if (dark) "light" else "dark"
                            settings.themeMode = themeMode
                        }
                    )
                    Tab.History -> HistoryScreen()
                    Tab.Settings -> SettingsScreen(
                        settings,
                        onChanged = { sourcesVersion++ },
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(settings: Settings, sourcesVersion: Int) {
    val ctx = LocalContext.current
    val historyStore = remember { HistoryStore(ctx) }

    val sources = remember(sourcesVersion) { settings.getSources() }
    var activeId by remember(sourcesVersion) { mutableStateOf(settings.activeSource()?.id) }
    val source = sources.firstOrNull { it.id == activeId } ?: sources.firstOrNull()

    if (source == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在「设置」中添加并保存 WebDAV 源", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val client = remember(source.id) { WebDavClient(source.url, source.user, source.pass) }

    val stack = remember(source.id) {
        // 重建从根目录到上次目录的完整路径链，保证「回上级/回主目录」可用
        val chain = mutableStateListOf(client.rootPath)
        val last = settings.lastPath
        if (!last.isNullOrBlank() && last.startsWith(client.rootPath) && last != client.rootPath) {
            val rel = last.removePrefix(client.rootPath).trim('/')
            if (rel.isNotEmpty()) {
                var cur = client.rootPath
                rel.split('/').filter { it.isNotEmpty() }.forEach { seg ->
                    cur += "$seg/"
                    chain.add(cur)
                }
            }
        }
        chain
    }
    val path = stack.last()
    var entries by remember(source.id) { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    var loading by remember(source.id) { mutableStateOf(false) }
    var refreshTick by remember(source.id) { mutableStateOf(0) }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(path, refreshTick) {
        loading = true; error = null
        try {
            entries = client.list(path)
            settings.lastPath = path
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            entries = null
        }
        loading = false
    }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.size - 1) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    decodeName(path).ifBlank { source.name },
                    maxLines = 1, overflow = TextOverflow.Ellipsis
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
                IconButton(onClick = { refreshTick++ }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.href }) { entry ->
                        ListItem(
                            leadingContent = {
                                if (!entry.isDir) {
                                    FileCover(historyStore, entry.href)
                                } else {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            headlineContent = {
                                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                if (!entry.isDir && entry.size > 0) Text(formatSize(entry.size))
                            },
                            trailingContent = {
                                if (entry.isDir) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            },
                            modifier = Modifier.clickable {
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
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

/** 文件列表中的封面缩略图：已有封面才显示，否则显示默认图标 */
@Composable
fun FileCover(store: HistoryStore, href: String) {
    val bitmap by produceState<ImageBitmap?>(null, href) {
        value = withContext(Dispatchers.IO) {
            val f = store.coverFile(href)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }
    }
    val b = bitmap
    if (b != null) {
        Image(
            b, null,
            Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun decodeName(path: String): String = runCatching {
    java.net.URLDecoder.decode(path.trimEnd('/').substringAfterLast('/'), "UTF-8")
}.getOrDefault(path)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }
    val comicStore = remember { ComicHistoryStore(ctx) }
    val settings = remember { Settings(ctx) }

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
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = seg == 0,
                onClick = { seg = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("视频") }
            SegmentedButton(
                selected = seg == 1,
                onClick = { seg = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("漫画") }
        }

        if (seg == 0) {
            // ---------- 视频历史 ----------
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无视频播放历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.href }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { startPlayer(ctx, item.href, item.title, item.positionMs) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cover = remember(item.href, resumeTick) {
                            val f = store.coverFile(item.href)
                            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                        }
                        Box(
                            Modifier
                                .size(112.dp, 63.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cover != null) {
                                Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Surface(
                                    Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "看到 ${formatMs(item.positionMs)} / ${formatMs(item.durationMs)} · ${fmt.format(Date(item.updatedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { item.progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                            )
                        }
                        IconButton(onClick = { store.remove(item.href); resumeTick++ }) {
                            Icon(Icons.Default.Delete, "删除记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
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
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (komgaLoader != null && komgaClient != null) {
                                    AsyncImage(
                                        model = SecureImage(komgaClient.bookThumbUrl(item.bookId)),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(63.dp, 90.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Surface(
                                        Modifier.size(63.dp, 90.dp).clip(RoundedCornerShape(6.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (item.seriesTitle.isNotBlank()) {
                                        Text(item.seriesTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "读到第 ${item.page} 页 / 共 ${item.pagesCount} 页 · ${fmt.format(Date(item.updatedAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { item.progress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp)
                                    )
                                }
                                IconButton(onClick = { comicStore.remove(item.bookId); resumeTick++ }) {
                                    Icon(Icons.Default.Delete, "删除记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onChanged: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
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

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- 外观主题 ----------
        item {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (m, label) ->
                    FilterChip(
                        selected = themeMode == m,
                        onClick = { settings.themeMode = m; onThemeChange(m) },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WebDAV 源", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { openForm(null) }) {
                    Icon(Icons.Default.Add, null)
                    Text("添加")
                }
            }
        }

        if (sources.isEmpty() && !showForm) {
            item {
                Text("还没有 WebDAV 源，点击「添加」创建。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(sources, key = { it.id }) { s ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = s.id == activeId,
                        onClick = {
                            settings.activeSourceId = s.id
                            activeId = s.id
                            onChanged()
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            s.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { openForm(s) }) {
                        Icon(Icons.Default.Edit, "编辑")
                    }
                    IconButton(onClick = {
                        sources = sources.filterNot { it.id == s.id }
                        settings.saveSources(sources)
                        onChanged()
                    }) {
                        Icon(Icons.Default.Delete, "删除")
                    }
                }
            }
        }

        if (showForm) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (editingId == null) "添加 WebDAV 源" else "编辑 WebDAV 源",
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedTextField(
                            value = fName, onValueChange = { fName = it },
                            label = { Text("名称（如：家里 NAS）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fUrl, onValueChange = { fUrl = it },
                            label = { Text("地址（如 http://192.168.1.10:5244/dav）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fUser, onValueChange = { fUser = it },
                            label = { Text("用户名（可空）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fPass, onValueChange = { fPass = it },
                            label = { Text("密码（可空）") },
                            singleLine = true,
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
                            }) { Text("保存") }
                            OutlinedButton(onClick = { showForm = false }) { Text("取消") }
                        }
                    }
                }
            }
        }

        // ---------- Komga 服务器 ----------
        item {
            Spacer(Modifier.height(8.dp))
            Text("Komga 服务器", style = MaterialTheme.typography.titleMedium)
        }
        item {
            var kUrl by remember { mutableStateOf(settings.komgaUrl) }
            var kUser by remember { mutableStateOf(settings.komgaUser) }
            var kPass by remember { mutableStateOf(settings.komgaPass) }
            var kMsg by remember { mutableStateOf<String?>(null) }
            var testing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = kUrl, onValueChange = { kUrl = it },
                        label = { Text("地址（如 http://192.168.1.10:25600）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kUser, onValueChange = { kUser = it },
                        label = { Text("邮箱") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = kPass, onValueChange = { kPass = it },
                        label = { Text("密码") },
                        singleLine = true,
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
                        }) { Text("保存") }
                        OutlinedButton(
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

        item {
            Spacer(Modifier.height(8.dp))
            Text("播放", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text("长按屏幕时的倍速：${"%.1f".format(lpSpeed)}x", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speedOptions.forEach { s ->
                    FilterChip(
                        selected = lpSpeed == s,
                        onClick = { lpSpeed = s; settings.longPressSpeed = s },
                        label = { Text("${"%.1f".format(s)}x") }
                    )
                }
            }
            Text(
                "长按屏幕任意位置临时加速，松开恢复正常速度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
