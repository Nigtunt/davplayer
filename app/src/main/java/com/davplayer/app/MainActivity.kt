package com.davplayer.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
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
    Browse("浏览"), History("历史"), Settings("设置")
}

fun startPlayer(ctx: Context, href: String, title: String, positionMs: Long = 0L) {
    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_HREF, href)
        putExtra(PlayerActivity.EXTRA_TITLE, title)
        putExtra(PlayerActivity.EXTRA_POSITION, positionMs)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var tab by remember { mutableStateOf(Tab.Browse) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Browse,
                        onClick = { tab = Tab.Browse },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text(Tab.Browse.label) })
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
                    Tab.Browse -> BrowseScreen(settings)
                    Tab.History -> HistoryScreen()
                    Tab.Settings -> SettingsScreen(settings)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(settings: Settings) {
    val ctx = LocalContext.current
    val client = remember(settings.serverUrl, settings.username, settings.password) {
        if (settings.serverUrl.isBlank()) null
        else runCatching { WebDavClient(settings.serverUrl, settings.username, settings.password) }.getOrNull()
    }
    if (client == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在「设置」中配置 WebDAV 地址", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val startPath = remember {
        val last = settings.lastPath
        if (!last.isNullOrBlank() && last.startsWith(client.rootPath)) last else client.rootPath
    }
    val stack = remember { mutableStateListOf(startPath) }
    val path = stack.last()
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
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
                    decodeName(path).ifBlank { "/" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (stack.size > 1) {
                    IconButton(onClick = { stack.removeAt(stack.size - 1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上级")
                    }
                }
            },
            actions = {
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
                                Icon(
                                    if (entry.isDir) Icons.Default.Folder else Icons.Default.Movie,
                                    null,
                                    tint = if (entry.isDir) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

private fun decodeName(path: String): String = runCatching {
    java.net.URLDecoder.decode(path.trimEnd('/').substringAfterLast('/'), "UTF-8")
}.getOrDefault(path)

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val store = remember { HistoryStore(ctx) }

    // 从播放器返回时刷新
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

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings) {
    var url by remember { mutableStateOf(settings.serverUrl) }
    var user by remember { mutableStateOf(settings.username) }
    var pass by remember { mutableStateOf(settings.password) }
    var lpSpeed by remember { mutableStateOf(settings.longPressSpeed) }
    val speedOptions = listOf(1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WebDAV 服务器", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; settings.serverUrl = it },
            label = { Text("地址（如 http://192.168.1.10:5244/dav）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it; settings.username = it },
            label = { Text("用户名（可空）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it; settings.password = it },
            label = { Text("密码（可空）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text("播放", style = MaterialTheme.typography.titleMedium)
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
            "配置立即生效。播放时长按屏幕任意位置即可临时加速，松开恢复正常速度。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
