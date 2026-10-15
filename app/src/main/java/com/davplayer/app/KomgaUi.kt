package com.davplayer.app

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.launch

sealed interface KomgaRoute {
    data object Home : KomgaRoute
    data class SeriesList(val libraryId: String?, val title: String) : KomgaRoute
    data class SeriesDetail(val id: String, val title: String) : KomgaRoute
    data class ReadListDetail(val id: String, val title: String) : KomgaRoute
}

@Composable
fun KomgaScreen(settings: Settings, dark: Boolean, onToggleTheme: () -> Unit) {
    val ctx = LocalContext.current
    if (settings.komgaUrl.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先在「设置」中配置并保存 Komga 服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val client = remember(settings.komgaUrl, settings.komgaUser, settings.komgaPass) {
        KomgaClient(settings.komgaUrl, settings.komgaUser, settings.komgaPass)
    }
    // 封面/页面图片需要带认证头，使用专用 ImageLoader
    val imageLoader = remember(client) {
        ImageLoader.Builder(ctx).okHttpClient(client.http).crossfade(true).build()
    }

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        val stack = remember { mutableStateListOf<KomgaRoute>(KomgaRoute.Home) }
        BackHandler(stack.size > 1) { stack.removeAt(stack.size - 1) }

        Column(Modifier.fillMaxSize()) {
            val route = stack.last()
            KomgaTopBar(
                title = when (val r = route) {
                    is KomgaRoute.Home -> "漫画"
                    is KomgaRoute.SeriesList -> r.title
                    is KomgaRoute.SeriesDetail -> r.title
                    is KomgaRoute.ReadListDetail -> r.title
                },
                canBack = stack.size > 1,
                onBack = { stack.removeAt(stack.size - 1) },
                dark = dark,
                onToggleTheme = onToggleTheme
            )
            when (val r = route) {
                is KomgaRoute.Home -> KomgaHome(client) { stack.add(it) }
                is KomgaRoute.SeriesList -> KomgaSeriesList(client, r.libraryId) { id, t ->
                    stack.add(KomgaRoute.SeriesDetail(id, t))
                }
                is KomgaRoute.SeriesDetail -> KomgaSeriesDetail(client, r.id)
                is KomgaRoute.ReadListDetail -> KomgaReadListDetail(client, r.id, r.title) {
                    stack.removeAt(stack.size - 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KomgaTopBar(
    title: String,
    canBack: Boolean,
    onBack: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        navigationIcon = {
            if (canBack) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
        actions = {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    if (dark) "切换亮色" else "切换暗色",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

// ---------------- 首页（书架） ----------------

@Composable
private fun KomgaHome(client: KomgaClient, onNavigate: (KomgaRoute) -> Unit) {
    var libraries by remember { mutableStateOf<List<KomgaLibrary>?>(null) }
    var onDeck by remember { mutableStateOf<List<KomgaBook>?>(null) }
    var latest by remember { mutableStateOf<List<KomgaSeries>?>(null) }
    var readLists by remember { mutableStateOf<List<KomgaReadList>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var showCreateList by remember { mutableStateOf(false) }
    var listActionMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        error = null
        try {
            libraries = client.libraries()
            onDeck = client.onDeck()
            latest = client.latestSeries()
            readLists = client.readLists()
        } catch (e: Exception) {
            error = e.message
        }
    }

    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("连接失败：$error", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { refreshTick++ }) { Text("重试") }
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        val deck = onDeck.orEmpty()
        if (deck.isNotEmpty()) {
            item { SectionHeader("继续阅读") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(deck, key = { it.id }) { book ->
                        CoverCard(
                            imageUrl = client.bookThumbUrl(book.id),
                            title = book.seriesTitle.ifBlank { book.title },
                            subtitle = book.title,
                            progress = if (book.pagesCount > 0) book.readPage.toFloat() / book.pagesCount else 0f,
                            onClick = { startReader(it, book.id, book.title, book.readPage, book.seriesTitle) }
                        )
                    }
                }
            }
        }

        val latestList = latest.orEmpty()
        if (latestList.isNotEmpty()) {
            item { Spacer(Modifier.height(20.dp)); SectionHeader("最新入库") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(latestList, key = { it.id }) { s ->
                        CoverCard(
                            imageUrl = client.seriesThumbUrl(s.id),
                            title = s.title,
                            subtitle = "${s.booksCount} 本",
                            progress = s.readProgress,
                            onClick = { onNavigate(KomgaRoute.SeriesDetail(s.id, s.title)) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("阅读列表")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showCreateList = true }) { Text("＋ 新建") }
                Spacer(Modifier.width(8.dp))
            }
        }

        val lists = readLists.orEmpty()
        if (lists.isEmpty()) {
            item {
                Text(
                    "暂无阅读列表",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lists, key = { it.id }) { rl ->
                        Column(
                            Modifier
                                .width(128.dp)
                                .clickable { onNavigate(KomgaRoute.ReadListDetail(rl.id, rl.name)) }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = client.readListThumbUrl(rl.id),
                                    contentDescription = rl.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(rl.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("${rl.bookIds.size} 本", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        listActionMsg?.let { msg ->
            item {
                Text(msg, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        item { Spacer(Modifier.height(20.dp)); SectionHeader("书库") }

        val libs = libraries
        if (libs == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            items(libs, key = { it.id }) { lib ->
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigate(KomgaRoute.SeriesList(lib.id, lib.name)) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            lib.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = { onNavigate(KomgaRoute.SeriesList(null, "全部系列")) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) { Text("浏览全部系列 →") }
            }
        }
    }

    // 新建阅读列表对话框
    if (showCreateList) {
        var name by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateList = false },
            title = { Text("新建阅读列表") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("列表名称") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) { err = "请输入名称"; return@TextButton }
                    scope.launch {
                        try {
                            client.createReadList(name.trim())
                            showCreateList = false
                            listActionMsg = "✓ 已创建「${name.trim()}」"
                            refreshTick++
                        } catch (e: Exception) {
                            err = e.message
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateList = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** 封面卡片：2:3 比例，底部渐变压暗 + 标题，阅读进度条 */
@Composable
private fun CoverCard(
    imageUrl: String,
    title: String,
    subtitle: String,
    progress: Float,
    onClick: (Context) -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .width(128.dp)
            .clickable { onClick(ctx) }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 底部渐变保证文字可读
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- 系列列表（书库） ----------------

@Composable
private fun KomgaSeriesList(
    client: KomgaClient,
    libraryId: String?,
    onOpenSeries: (String, String) -> Unit
) {
    var items by remember { mutableStateOf<List<KomgaSeries>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var isLast by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            val p = if (reset) 0 else page
            val r = client.series(page = p, libraryId = libraryId, search = searchQuery.ifBlank { null })
            items = if (reset) r.items else items + r.items
            isLast = r.isLast
            page = p + 1
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    LaunchedEffect(searchQuery) {
        page = 0
        load(reset = true)
    }

    // 滚动到底自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLast && !loading) load(reset = false)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("搜索系列…") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            trailingIcon = {
                TextButton(onClick = { searchQuery = search }) { Text("搜索") }
            }
        )

        if (items.isEmpty() && loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (items.isEmpty() && error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { page = 0; /* retry */ searchQuery = searchQuery }) { Text("重试") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { s ->
                    SeriesGridCard(client, s, onOpenSeries)
                }
                if (!isLast) {
                    item {
                        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesGridCard(
    client: KomgaClient,
    s: KomgaSeries,
    onOpenSeries: (String, String) -> Unit
) {
    Column(Modifier.clickable { onOpenSeries(s.id, s.title) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = client.seriesThumbUrl(s.id),
                contentDescription = s.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (s.booksUnreadCount == 0 && s.booksCount > 0) {
                Icon(
                    Icons.Default.CheckCircle, "已读完",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                )
            }
            if (s.readProgress > 0f) {
                LinearProgressIndicator(
                    progress = { s.readProgress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
        Text(
            "${s.booksCount} 本" + if (s.booksUnreadCount > 0) " · ${s.booksUnreadCount} 未读" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------- 系列详情 ----------------

@Composable
private fun KomgaSeriesDetail(client: KomgaClient, seriesId: String) {
    var books by remember { mutableStateOf<List<KomgaBook>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addToListBook by remember { mutableStateOf<KomgaBook?>(null) }
    var listMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seriesId) {
        try {
            books = client.seriesBooks(seriesId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    when {
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
        }
        books == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val list = books!!
            val readCount = list.count { it.readCompleted }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "共 ${list.size} 本 · 已读完 $readCount 本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (list.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { readCount.toFloat() / list.size },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    listMsg?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(list, key = { it.id }) { book ->
                    BookRow(
                        client = client,
                        book = book,
                        trailing = {
                            IconButton(onClick = { addToListBook = book }) {
                                Icon(Icons.Default.PlaylistAdd, "添加到阅读列表",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }

    addToListBook?.let { book ->
        AddToReadListDialog(
            client = client,
            book = book,
            onDismiss = { addToListBook = null },
            onDone = { msg -> listMsg = msg }
        )
    }
}

/** 书籍行：封面 + 标题 + 进度，trailing 插槽放操作按钮 */
@Composable
private fun BookRow(
    client: KomgaClient,
    book: KomgaBook,
    trailing: @Composable () -> Unit = {}
) {
    val ctx = LocalContext.current
    Surface(
        Modifier
            .fillMaxWidth()
            .clickable { startReader(ctx, book.id, book.title, book.readPage, book.seriesTitle) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = client.bookThumbUrl(book.id),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp, 80.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(4.dp))
                val status = when {
                    book.readCompleted -> "已读完"
                    book.inProgress -> "读到第 ${book.readPage} 页 / 共 ${book.pagesCount} 页"
                    else -> "共 ${book.pagesCount} 页 · 未读"
                }
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.inProgress && book.pagesCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { book.readPage.toFloat() / book.pagesCount },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (book.readCompleted) {
                Icon(Icons.Default.CheckCircle, "已读完",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
            trailing()
        }
    }
}

// ---------------- 阅读列表详情 ----------------

@Composable
private fun KomgaReadListDetail(
    client: KomgaClient,
    readListId: String,
    title: String,
    onDeleted: () -> Unit
) {
    var books by remember { mutableStateOf<List<KomgaBook>?>(null) }
    var bookIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(readListId, refreshTick) {
        error = null
        try {
            bookIds = client.readLists().firstOrNull { it.id == readListId }?.bookIds ?: emptyList()
            books = client.readListBooks(readListId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${books?.size ?: 0} 本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除列表", color = MaterialTheme.colorScheme.error)
            }
        }
        msg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
            }
            books == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            books!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("列表还是空的，去系列详情页把漫画加进来", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(books!!, key = { it.id }) { book ->
                    BookRow(
                        client = client,
                        book = book,
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        client.updateReadListBooks(readListId, bookIds - book.id)
                                        msg = "✓ 已移除「${book.title}」"
                                        refreshTick++
                                    } catch (e: Exception) {
                                        msg = "✗ ${e.message}"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.RemoveCircleOutline, "从列表移除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除阅读列表") },
            text = { Text("确定删除「$title」吗？书籍本身不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { client.deleteReadList(readListId) } catch (_: Exception) {}
                        confirmDelete = false
                        onDeleted()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

// ---------------- 添加到阅读列表对话框 ----------------

@Composable
private fun AddToReadListDialog(
    client: KomgaClient,
    book: KomgaBook,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var lists by remember { mutableStateOf<List<KomgaReadList>?>(null) }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        lists = runCatching { client.readLists() }.getOrDefault(emptyList())
    }

    fun addTo(list: KomgaReadList) {
        scope.launch {
            busy = true
            try {
                client.updateReadListBooks(list.id, (list.bookIds + book.id).distinct())
                onDone("✓ 已加入「${list.name}」")
                onDismiss()
            } catch (e: Exception) {
                err = e.message
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("添加到阅读列表") },
        text = {
            Column {
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val l = lists
                if (l == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(l, key = { it.id }) { rl ->
                            val contains = book.id in rl.bookIds
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !contains && !busy) { addTo(rl) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(rl.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${rl.bookIds.size} 本", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (contains) {
                                    Text("已在列表", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it },
                            label = { Text("新列表名称") }, singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = newName.isNotBlank() && !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val id = client.createReadList(newName.trim())
                                        client.updateReadListBooks(id, listOf(book.id))
                                        onDone("✓ 已创建「${newName.trim()}」并加入")
                                        onDismiss()
                                    } catch (e: Exception) {
                                        err = e.message
                                    }
                                    busy = false
                                }
                            }
                        ) { Text("新建并添加") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("关闭") }
        }
    )
}
