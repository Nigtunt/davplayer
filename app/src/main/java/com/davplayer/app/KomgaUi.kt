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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
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

fun startReader(ctx: Context, bookId: String, title: String, page: Int = 0) {
    ctx.startActivity(Intent(ctx, ReaderActivity::class.java).apply {
        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
        putExtra(ReaderActivity.EXTRA_TITLE, title)
        putExtra(ReaderActivity.EXTRA_PAGE, page)
    })
}

sealed interface KomgaRoute {
    data object Home : KomgaRoute
    data class SeriesList(val libraryId: String?, val title: String) : KomgaRoute
    data class SeriesDetail(val id: String, val title: String) : KomgaRoute
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
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        error = null
        try {
            libraries = client.libraries()
            onDeck = client.onDeck()
            latest = client.latestSeries()
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
                            onClick = { startReader(it, book.id, book.title, book.readPage) }
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
    val ctx = LocalContext.current
    var books by remember { mutableStateOf<List<KomgaBook>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
                    Spacer(Modifier.height(8.dp))
                }
                items(list, key = { it.id }) { book ->
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                startReader(ctx, book.id, book.title, book.readPage)
                            },
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
                        }
                    }
                }
            }
        }
    }
}
