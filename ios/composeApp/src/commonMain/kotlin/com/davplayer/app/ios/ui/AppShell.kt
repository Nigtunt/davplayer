package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.ComicStore
import com.davplayer.app.ios.data.DavSource
import com.davplayer.app.ios.data.KomgaBook
import com.davplayer.app.ios.data.KomgaClient
import com.davplayer.app.ios.data.KomgaImage
import com.davplayer.app.ios.data.PlayProgress
import com.davplayer.app.ios.data.SourceStore
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh
import com.davplayer.app.ios.theme.TextMain

/** 与 Android 端一致的四个顶层入口 */
enum class AppTab(val label: String, val icon: ImageVector) {
    Browse("浏览", Icons.Filled.Folder),
    Komga("漫画", Icons.Filled.MenuBook),
    History("历史", Icons.Filled.History),
    Settings("我的", Icons.Filled.Settings)
}

/** 宽度达到这个值切横屏双栏（左 tab 栏 + 右内容） */
private val WideBreakpoint = 840.dp

@Composable
fun AppShell(
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    var tab by remember { mutableStateOf(AppTab.Browse) }
    var activeSourceId by remember { mutableStateOf<String?>(SourceStore.sources.firstOrNull()?.id) }
    var initialPath by remember { mutableStateOf<String?>(null) }
    var highlightHref by remember { mutableStateOf<String?>(null) }
    var komgaResume by remember { mutableStateOf<KomgaBook?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }

    val activeSource = SourceStore.sourceById(activeSourceId ?: "")
        ?: SourceStore.sources.firstOrNull()

    val openSource: (DavSource, String?, String?) -> Unit = { source, path, href ->
        activeSourceId = source.id
        initialPath = path
        highlightHref = href
        tab = AppTab.Browse
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wide = maxWidth >= WideBreakpoint

        // TabContent 只在这一处 compose。横竖屏切换时只改 padding，
        // 不移动 Compose 节点，所以内部状态（目录栈、Komga 阅读页、播放）不会丢，
        // 也避免 movableContentOf 在 iOS 布局树里搬节点导致的崩溃。
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (wide) 84.dp else 0.dp,
                    bottom = if (wide) 0.dp else 56.dp
                )
        ) {
            TabContent(
                tab = tab,
                source = activeSource,
                initialPath = initialPath,
                highlightHref = highlightHref,
                komgaResume = komgaResume,
                onOpenSource = openSource,
                onManageSources = { tab = AppTab.Settings },
                onOpenPrivacy = { showPrivacy = true },
                onResumeComic = {
                    komgaResume = it
                    tab = AppTab.Komga
                },
                onKomgaConsumed = { komgaResume = null },
                onHistoryConsumed = {
                    highlightHref = null
                    initialPath = null
                },
                themeMode = themeMode,
                onThemeChange = onThemeChange
            )
        }

        if (wide) {
            TabRail(tab) { tab = it }
        } else {
            Box(Modifier.align(Alignment.BottomCenter)) {
                BottomTabs(tab) { tab = it }
            }
        }
    }

    if (showPrivacy) {
        PrivacySettingsDialog(onDismiss = { showPrivacy = false })
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
    source: DavSource?,
    initialPath: String?,
    highlightHref: String?,
    komgaResume: KomgaBook?,
    onOpenSource: (DavSource, String?, String?) -> Unit,
    onManageSources: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onResumeComic: (KomgaBook) -> Unit,
    onKomgaConsumed: () -> Unit,
    onHistoryConsumed: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    when (tab) {
        AppTab.Browse -> {
            if (source == null) {
                EmptySources(onManageSources)
            } else {
                BrowserScreen(
                    source = source,
                    initialPath = initialPath,
                    highlightHref = highlightHref,
                    onSwitchSource = { onOpenSource(it, null, null) },
                    onExit = onManageSources,
                    onOpenSettings = onOpenPrivacy,
                    onHistoryConsumed = onHistoryConsumed
                )
            }
        }

        AppTab.Komga -> KomgaTab(
            onOpenSettings = onManageSources,
            initialBook = komgaResume,
            onConsumed = onKomgaConsumed
        )

        AppTab.History -> HistoryTab(
            onResume = { progress ->
                val target = SourceStore.sourceById(progress.sourceId)
                if (target != null) {
                    onOpenSource(target, parentPathOf(progress.path), progress.path)
                }
            },
            onResumeComic = onResumeComic
        )

        AppTab.Settings -> SettingsTab(
            onOpenSource = { onOpenSource(it, null, null) },
            onOpenPrivacy = onOpenPrivacy,
            themeMode = themeMode,
            onThemeChange = onThemeChange
        )
    }
}

@Composable
private fun TabRail(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Column(
        Modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(tab) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Brand.copy(alpha = 0.10f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Brand else Muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Brand else Muted
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(SurfaceBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.clickable { onSelect(tab) }.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Brand.copy(alpha = 0.10f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Brand else Muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Brand else Muted
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    onOpenSource: (DavSource) -> Unit,
    onOpenPrivacy: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DavSource?>(null) }
    var showLog by remember { mutableStateOf(false) }

    if (showLog) {
        LogScreen(onBack = { showLog = false })
        return
    }

    if (editorOpen) {
        SourceEditorScreen(
            existing = editing,
            canCancel = SourceStore.sources.isNotEmpty(),
            onSave = { source ->
                SourceStore.addOrUpdate(source)
                editorOpen = false
                editing = null
            },
            onCancel = {
                editorOpen = false
                editing = null
            }
        )
    } else {
        SourceListScreen(
            sources = SourceStore.sources,
            history = emptyList(),
            onOpen = onOpenSource,
            onResume = {},
            onAdd = {
                editing = null
                editorOpen = true
            },
            onEdit = {
                editing = it
                editorOpen = true
            },
            onDelete = { SourceStore.remove(it.id) },
            onOpenSettings = onOpenPrivacy,
            headerContent = {
                ThemeModeCard(themeMode, onThemeChange)
                Spacer(Modifier.height(18.dp))
            },
            footer = {
                Column {
                    KomgaSettingsCard()
                    Spacer(Modifier.height(12.dp))
                    LogCard(onView = { showLog = true })
                }
            }
        )
    }
}

@Composable
private fun ThemeModeCard(themeMode: String, onChange: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("外观主题", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Spacer(Modifier.height(3.dp))
        Text("默认跟随系统，也可以手动固定浅色或深色", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice("跟随系统", "system", themeMode, onChange)
            ThemeChoice("浅色", "light", themeMode, onChange)
            ThemeChoice("深色", "dark", themeMode, onChange)
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    value: String,
    selectedValue: String,
    onChange: (String) -> Unit
) {
    val selected = selectedValue == value
    Box(
        Modifier
            .background(if (selected) Brand else SurfaceHigh, RoundedCornerShape(50))
            .clickable { onChange(value) }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Muted
        )
    }
}

@Composable
private fun HistoryTab(
    onResume: (PlayProgress) -> Unit,
    onResumeComic: (KomgaBook) -> Unit
) {
    val history = SourceStore.history
    var segment by remember { mutableStateOf(0) }
    val config = ComicStore.config
    val client = remember(config) {
        if (config.isConfigured) KomgaClient(config.url, config.email, config.pass) else null
    }
    var comics by remember(client) { mutableStateOf<List<KomgaBook>?>(null) }
    var comicError by remember(client) { mutableStateOf<String?>(null) }
    var comicReload by remember(client) { mutableStateOf(0) }

    LaunchedEffect(client, segment, comicReload) {
        if (segment == 1 && client != null) {
            comics = null
            comicError = null
            runCatching { client.readBooks() }
                .onSuccess { comics = it }
                .onFailure { comicError = it.message ?: "加载失败" }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("历史", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text("视频播放与漫画阅读记录", fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(SurfaceHigh).padding(3.dp)
            ) {
                HistoryChip("视频", selected = segment == 0, modifier = Modifier.weight(1f)) { segment = 0 }
                HistoryChip("漫画", selected = segment == 1, modifier = Modifier.weight(1f)) { segment = 1 }
            }

            Spacer(Modifier.height(16.dp))

            if (segment == 0) {
                if (history.isEmpty()) {
                    Text("暂无视频播放历史", color = Muted, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.forEach { item ->
                            HistoryRow(
                                item = item,
                                sourceName = SourceStore.sourceById(item.sourceId)?.name.orEmpty(),
                                onClick = { onResume(item) }
                            )
                        }
                    }
                }
            } else {
                when {
                    client == null -> Text("请先在「我的」里配置 Komga 服务器", color = Muted, fontSize = 13.sp)
                    comicError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("漫画历史加载失败：$comicError", color = Danger, fontSize = 13.sp)
                        TextButton(onClick = { comicReload++ }) { Text("重试", color = Brand) }
                    }
                    comics == null -> CircularProgressIndicator(color = Brand)
                    comics!!.isEmpty() -> {
                        Text("暂无漫画阅读历史", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("服务端没有进行中或已读的书籍", color = Muted, fontSize = 12.sp)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        comics!!.forEach { item ->
                            ComicHistoryRow(
                                item = item,
                                client = client,
                                onClick = { onResumeComic(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) SurfaceBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Brand else Muted
        )
    }
}

@Composable
private fun ComicHistoryRow(item: KomgaBook, client: KomgaClient?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(62.dp).height(90.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceHigh)
        ) {
            if (client != null && !ComicStore.safeMode) {
                KomgaImage(
                    url = client.bookThumbUrl(item.id),
                    auth = client.authHeader,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无图", fontSize = 11.sp, color = Muted)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                item.seriesTitle,
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val time = item.progressTimestamp.take(16).replace('T', ' ')
            Text(
                if (time.isBlank()) "服务端进度：第 ${item.readPage} / ${item.pagesCount} 页"
                else "第 ${item.readPage} / ${item.pagesCount} 页 · $time",
                fontSize = 11.sp,
                color = Muted
            )
            Spacer(Modifier.height(6.dp))
            val fraction = if (item.pagesCount > 0) {
                (item.readPage.toFloat() / item.pagesCount).coerceIn(0f, 1f)
            } else 0f
            Box(
                Modifier.fillMaxWidth().height(3.dp).background(SurfaceHigh, RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(Brand, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptySources(onManageSources: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有 WebDAV 源", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("先到「我的」里添加一个源", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(onClick = onManageSources) {
                Text("去添加")
            }
        }
    }
}

/** /dav/115/movie.mkv -> /dav/115/ */
private fun parentPathOf(path: String): String {
    val parent = path.trimEnd('/').substringBeforeLast('/', "")
    return when {
        parent.isBlank() -> "/"
        parent.endsWith("/") -> parent
        else -> "$parent/"
    }
}
