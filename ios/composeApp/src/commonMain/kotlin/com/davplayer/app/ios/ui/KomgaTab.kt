package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.ComicStore
import com.davplayer.app.ios.data.KomgaBook
import com.davplayer.app.ios.data.KomgaClient
import com.davplayer.app.ios.data.KomgaConfig
import com.davplayer.app.ios.data.KomgaImage
import com.davplayer.app.ios.data.KomgaImages
import com.davplayer.app.ios.data.KomgaLibrary
import com.davplayer.app.ios.data.KomgaPage
import com.davplayer.app.ios.data.KomgaReadList
import com.davplayer.app.ios.data.KomgaSeries
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import com.davplayer.app.ios.theme.SurfaceHigh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface KomgaRoute {
    data object Home : KomgaRoute
    data class SeriesList(val libraryId: String?, val title: String) : KomgaRoute
    data class SeriesDetail(val id: String, val title: String) : KomgaRoute
    data class ReadListDetail(val id: String, val title: String) : KomgaRoute
    data class Reader(val book: KomgaBook) : KomgaRoute
}

@Composable
fun KomgaTab(
    onOpenSettings: () -> Unit,
    initialBook: KomgaBook? = null,
    onConsumed: () -> Unit = {}
) {
    val config = ComicStore.config
    if (!config.isConfigured) {
        KomgaUnconfigured(onOpenSettings)
        return
    }

    val client = remember(config) { KomgaClient(config.url, config.email, config.pass) }
    var stack by remember(config) {
        mutableStateOf<List<KomgaRoute>>(
            if (initialBook != null) {
                listOf(KomgaRoute.Home, KomgaRoute.Reader(initialBook))
            } else {
                listOf(KomgaRoute.Home)
            }
        )
    }

    LaunchedEffect(initialBook) {
        if (initialBook != null) {
            stack = listOf(KomgaRoute.Home, KomgaRoute.Reader(initialBook))
            onConsumed()
        }
    }

    val route = stack.last()
    Column(Modifier.fillMaxSize().background(Bg)) {
        if (route !is KomgaRoute.Reader) {
            KomgaTopBar(
                title = when (route) {
                    KomgaRoute.Home -> "漫画"
                    is KomgaRoute.SeriesList -> route.title
                    is KomgaRoute.SeriesDetail -> route.title
                    is KomgaRoute.ReadListDetail -> route.title
                    is KomgaRoute.Reader -> route.book.title
                },
                canBack = stack.size > 1,
                onBack = { stack = stack.dropLast(1) }
            )
            HorizontalDivider(color = Hairline, thickness = 1.dp)
        }

        Box(Modifier.weight(1f)) {
            when (route) {
                KomgaRoute.Home -> KomgaHome(
                    client = client,
                    onOpenSeries = { stack = stack + KomgaRoute.SeriesDetail(it.id, it.title) },
                    onOpenBook = { stack = stack + KomgaRoute.Reader(it) },
                    onOpenLibrary = { stack = stack + KomgaRoute.SeriesList(it.id, it.name) },
                    onOpenReadList = { stack = stack + KomgaRoute.ReadListDetail(it.id, it.name) },
                    onOpenAll = { stack = stack + KomgaRoute.SeriesList(null, "全部系列") }
                )

                is KomgaRoute.SeriesList -> SeriesListScreen(
                    client = client,
                    libraryId = route.libraryId,
                    onOpenSeries = { stack = stack + KomgaRoute.SeriesDetail(it.id, it.title) }
                )

                is KomgaRoute.SeriesDetail -> SeriesDetailScreen(
                    client = client,
                    seriesId = route.id,
                    seriesTitle = route.title,
                    onOpenBook = { stack = stack + KomgaRoute.Reader(it) }
                )

                is KomgaRoute.ReadListDetail -> ReadListDetailScreen(
                    client = client,
                    readListId = route.id,
                    title = route.title,
                    onDeleted = { stack = stack.dropLast(1) }
                )

                is KomgaRoute.Reader -> ReaderScreen(
                    client = client,
                    book = route.book,
                    onBack = { stack = stack.dropLast(1) }
                )
            }
        }
    }
}

@Composable
private fun KomgaTopBar(
    title: String,
    canBack: Boolean,
    onBack: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canBack) {
            TextButton(onClick = onBack) { Text("←", fontSize = 18.sp, color = Brand) }
        }
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KomgaUnconfigured(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📚", fontSize = 38.sp)
            Spacer(Modifier.height(10.dp))
            Text("还没有配置 Komga 服务器", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("去「我的」填写服务器地址和账号", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenSettings) { Text("去「我的」配置") }
        }
    }
}

// ---------- 服务器配置 ----------

@Composable
private fun KomgaSetupScreen(
    initial: KomgaConfig,
    canCancel: Boolean = false,
    onCancel: () -> Unit = {},
    onSaved: () -> Unit
) {
    var url by remember(initial) {
        mutableStateOf(initial.url.ifBlank { "http://192.168.1.10:25600" })
    }
    var email by remember(initial) { mutableStateOf(initial.email) }
    var pass by remember(initial) { mutableStateOf(initial.pass) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            if (canCancel) {
                TextButton(onClick = onCancel) { Text("← 返回", fontSize = 12.sp, color = Muted) }
                Spacer(Modifier.height(6.dp))
            }
            Text("Komga 服务器", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("连接到 Komga 后浏览书架、系列和阅读器", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("地址（如 http://IP:25600）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("邮箱 / 用户名") }, singleLine = true,
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = Danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    scope.launch {
                        testing = true
                        error = null
                        val ok = KomgaClient(url, email, pass).testConnection()
                        testing = false
                        if (ok) {
                            ComicStore.saveConfig(url, email, pass)
                            onSaved()
                        } else {
                            error = "连接失败：请检查地址、邮箱和密码"
                        }
                    }
                },
                enabled = url.isNotBlank() && !testing,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("保存并测试连接")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Komga 默认端口 25600；账号是 Komga 的登录邮箱",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}

// ---------- 首页 ----------

@Composable
private fun KomgaHome(
    client: KomgaClient,
    onOpenSeries: (KomgaSeries) -> Unit,
    onOpenBook: (KomgaBook) -> Unit,
    onOpenLibrary: (KomgaLibrary) -> Unit,
    onOpenReadList: (KomgaReadList) -> Unit,
    onOpenAll: () -> Unit
) {
    var libraries by remember(client) { mutableStateOf<List<KomgaLibrary>>(emptyList()) }
    var onDeck by remember(client) { mutableStateOf<List<KomgaBook>>(emptyList()) }
    var latest by remember(client) { mutableStateOf<List<KomgaSeries>>(emptyList()) }
    var readLists by remember(client) { mutableStateOf<List<KomgaReadList>>(emptyList()) }
    var loading by remember(client) { mutableStateOf(true) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(client, reload) {
        loading = true
        error = null
        runCatching {
            libraries = client.libraries()
            onDeck = client.onDeck()
            latest = client.latestSeries()
            readLists = client.readLists()
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    when {
        loading -> KomgaLoading()
        error != null -> KomgaError(error!!) { reload++ }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (onDeck.isNotEmpty()) {
                item {
                    SectionTitle("继续阅读")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        lazyItems(onDeck, key = { it.id }) { book ->
                            CoverCard(
                                title = book.title,
                                subtitle = if (book.pagesCount > 0) "第 ${book.readPage} / ${book.pagesCount} 页" else "",
                                fraction = if (book.pagesCount > 0) book.readPage.toFloat() / book.pagesCount else 0f,
                                coverUrl = client.bookThumbUrl(book.id),
                                auth = client.authHeader,
                                onClick = { onOpenBook(book) }
                            )
                        }
                    }
                }
            }

            if (latest.isNotEmpty()) {
                item {
                    SectionTitle("最新入库")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        lazyItems(latest, key = { it.id }) { series ->
                            CoverCard(
                                title = series.title,
                                subtitle = "${series.booksCount} 本",
                                fraction = series.readProgress,
                                coverUrl = client.seriesThumbUrl(series.id),
                                auth = client.authHeader,
                                onClick = { onOpenSeries(series) }
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("书库")
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    libraries.forEach { library ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(SurfaceBg, RoundedCornerShape(12.dp))
                                .clickable { onOpenLibrary(library) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📚", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                library.name.ifBlank { "书库" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text("›", fontSize = 18.sp, color = Muted)
                        }
                    }
                    TextButton(onClick = onOpenAll) {
                        Text("浏览全部系列 →", fontSize = 13.sp, color = Brand)
                    }
                }
            }

            item {
                SectionTitle("阅读列表")
                if (readLists.isEmpty()) {
                    Text(
                        "暂无阅读列表（可在书籍详情里新建并添加）",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        lazyItems(readLists, key = { it.id }) { list ->
                            CoverCard(
                                title = list.name,
                                subtitle = "${list.bookIds.size} 本",
                                fraction = 0f,
                                coverUrl = client.readListThumbUrl(list.id),
                                auth = client.authHeader,
                                onClick = { onOpenReadList(list) }
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = { reload++ },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) { Text("刷新", fontSize = 12.sp, color = Muted) }
            }
        }
    }
}

@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    fraction: Float,
    coverUrl: String,
    auth: String,
    onClick: () -> Unit
) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .width(112.dp)
                .height(158.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceHigh)
        ) {
            KomgaImage(
                url = coverUrl,
                auth = auth,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (fraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Brand)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 10.sp, color = Muted, maxLines = 1)
        }
    }
}

// ---------- 系列列表 ----------

@Composable
private fun SeriesListScreen(
    client: KomgaClient,
    libraryId: String?,
    onOpenSeries: (KomgaSeries) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<KomgaSeries>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var isLast by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    LaunchedEffect(client, libraryId, reload) {
        loading = true
        error = null
        items = emptyList()
        page = 0
        runCatching {
            client.series(page = 0, size = 30, libraryId = libraryId, search = query.ifBlank { null })
        }.onSuccess {
            items = it.content
            isLast = it.last
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    fun loadMore() {
        if (loading || isLast) return
        scope.launch {
            loading = true
            runCatching {
                client.series(page = page + 1, size = 30, libraryId = libraryId, search = query.ifBlank { null })
            }.onSuccess {
                items = items + it.content
                page += 1
                isLast = it.last
            }.onFailure {
                error = it.message ?: "加载失败"
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索系列", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { reload++ }, shape = RoundedCornerShape(999.dp)) {
                Text("搜索", fontSize = 12.sp)
            }
        }

        if (error != null) {
            KomgaError(error!!) { reload++ }
        } else if (loading && items.isEmpty()) {
            KomgaLoading()
        } else if (items.isEmpty()) {
            KomgaEmpty("没有找到系列")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                gridItems(items, key = { it.id }) { series ->
                    Column(Modifier.clickable { onOpenSeries(series) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceHigh)
                        ) {
                            KomgaImage(
                                url = client.seriesThumbUrl(series.id),
                                auth = client.authHeader,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            series.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${series.booksCount} 本 · ${series.booksUnreadCount} 未读",
                            fontSize = 10.sp,
                            color = Muted,
                            maxLines = 1
                        )
                    }
                }

                if (!isLast) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Button(onClick = { loadMore() }, shape = RoundedCornerShape(999.dp)) {
                                Text(if (loading) "加载中…" else "加载更多", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 系列详情 ----------

@Composable
private fun SeriesDetailScreen(
    client: KomgaClient,
    seriesId: String,
    seriesTitle: String,
    onOpenBook: (KomgaBook) -> Unit
) {
    var books by remember(client, seriesId) { mutableStateOf<List<KomgaBook>>(emptyList()) }
    var loading by remember(client, seriesId) { mutableStateOf(true) }
    var error by remember(client, seriesId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var addBook by remember { mutableStateOf<KomgaBook?>(null) }

    LaunchedEffect(client, seriesId, reload) {
        loading = true
        error = null
        runCatching { client.seriesBooks(seriesId) }
            .onSuccess { books = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    when {
        loading -> KomgaLoading()
        error != null -> KomgaError(error!!) { reload++ }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(seriesTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val read = books.count { it.readCompleted }
                Text("共 ${books.size} 本 · 已读完 $read 本", fontSize = 12.sp, color = Muted)
            }

            lazyItems(books, key = { it.id }) { book ->
                BookRow(
                    client = client,
                    book = book,
                    onClick = { onOpenBook(book) },
                    onAdd = { addBook = book }
                )
            }
        }
    }

    addBook?.let { book ->
        AddToReadListDialog(client, book) { addBook = null }
    }
}

@Composable
private fun BookRow(
    client: KomgaClient,
    book: KomgaBook,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceHigh)
        ) {
            KomgaImage(
                url = client.bookThumbUrl(book.id),
                auth = client.authHeader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(statusText(book), fontSize = 11.sp, color = Muted)
            if (book.pagesCount > 0) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(book.readPage.toFloat() / book.pagesCount)
            }
        }
        TextButton(onClick = onAdd) { Text("＋", fontSize = 16.sp, color = Muted) }
        if (book.readCompleted) {
            Text("✓", fontSize = 16.sp, color = Brand)
        }
    }
}

private fun statusText(book: KomgaBook): String = when {
    book.readCompleted -> "已读完"
    book.readPage > 0 -> "读到第 ${book.readPage} 页 / 共 ${book.pagesCount} 页"
    else -> "共 ${book.pagesCount} 页 · 未读"
}

@Composable
private fun AddToReadListDialog(
    client: KomgaClient,
    book: KomgaBook,
    onDismiss: () -> Unit
) {
    var lists by remember { mutableStateOf<List<KomgaReadList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(client, book.id) {
        lists = runCatching { client.readLists() }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入阅读列表") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
            ) {
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Brand) }
                } else {
                    lists.forEach { list ->
                        val already = book.id in list.bookIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) {
                                    scope.launch {
                                        runCatching {
                                            client.updateReadListBooks(
                                                list.id,
                                                (list.bookIds + book.id).distinct()
                                            )
                                        }
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                list.name,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (already) "已在列表" else "加入",
                                fontSize = 11.sp,
                                color = if (already) Muted else Brand
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新列表名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val id = client.createReadList(newName.trim())
                                    client.updateReadListBooks(id, listOf(book.id))
                                }
                                onDismiss()
                            }
                        }
                    ) { Text("新建并添加", fontSize = 12.sp, color = Brand) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ReadListDetailScreen(
    client: KomgaClient,
    readListId: String,
    title: String,
    onDeleted: () -> Unit
) {
    var books by remember(client, readListId) { mutableStateOf<List<KomgaBook>>(emptyList()) }
    var loading by remember(client, readListId) { mutableStateOf(true) }
    var error by remember(client, readListId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(client, readListId, reload) {
        loading = true
        error = null
        runCatching { client.readListBooks(readListId) }
            .onSuccess { books = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    when {
        loading -> KomgaLoading()
        error != null -> KomgaError(error!!) { reload++ }
        else -> Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("共 ${books.size} 本", fontSize = 12.sp, color = Muted)
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除列表", fontSize = 12.sp, color = Danger)
                }
            }

            if (books.isEmpty()) {
                Box(Modifier.weight(1f)) { KomgaEmpty("列表是空的，去系列详情添加书籍") }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lazyItems(books, key = { it.id }) { book ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(SurfaceBg, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(56.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceHigh)
                            ) {
                                KomgaImage(
                                    url = client.bookThumbUrl(book.id),
                                    auth = client.authHeader,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    book.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(statusText(book), fontSize = 11.sp, color = Muted)
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            client.updateReadListBooks(
                                                readListId,
                                                books.map { it.id } - book.id
                                            )
                                        }
                                        reload++
                                    }
                                }
                            ) { Text("移除", fontSize = 11.sp, color = Muted) }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除阅读列表") },
            text = { Text("确定删除「$title」吗？书籍不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { client.deleteReadList(readListId) }
                            onDeleted()
                        }
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

// ---------- 阅读器 ----------

@Composable
private fun ReaderScreen(client: KomgaClient, book: KomgaBook, onBack: () -> Unit) {
    var pages by remember(client, book.id) { mutableStateOf<List<KomgaPage>>(emptyList()) }
    var loading by remember(client, book.id) { mutableStateOf(true) }
    var error by remember(client, book.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(client, book.id, reload) {
        loading = true
        error = null
        runCatching { client.bookPages(book.id) }
            .onSuccess { list ->
                pages = if (list.isEmpty() && book.pagesCount > 0) {
                    (1..book.pagesCount).map { KomgaPage(number = it) }
                } else list
            }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    when {
        loading -> KomgaLoading()
        error != null -> KomgaError(error!!) { reload++ }
        pages.isEmpty() -> KomgaEmpty("这本没有可读页面")
        else -> ReaderContent(client, book, pages, onBack)
    }
}

@Composable
private fun ReaderContent(
    client: KomgaClient,
    book: KomgaBook,
    pages: List<KomgaPage>,
    onBack: () -> Unit
) {
    val initialPage = (book.readPage.coerceAtLeast(1) - 1).coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    var mode by remember { mutableStateOf(0) } // 0 LTR, 1 RTL, 2 vertical
    var zoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, mode) {
        if (mode != 2) {
            val next = pagerState.currentPage + 1
            if (next <= pages.lastIndex) {
                KomgaImages.load(client.pageUrl(book.id, pages[next].number), client.authHeader)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage + 1
        ComicStore.record(book.id, book.seriesTitle, book.title, page, pages.size)
        delay(900)
        runCatching { client.markReadProgress(book.id, page, page >= pages.size) }
    }

    LaunchedEffect(mode) {
        if (mode == 2) {
            listState.scrollToItem(pagerState.currentPage)
        } else {
            pagerState.scrollToPage(listState.firstVisibleItemIndex)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (showControls) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("←", fontSize = 18.sp, color = Color.White) }
                Text(
                    book.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ReaderModeChip("左→右", mode == 0) { mode = 0 }
                ReaderModeChip("右→左", mode == 1) { mode = 1 }
                ReaderModeChip("竖向", mode == 2) { mode = 2 }
            }
        }

        Box(Modifier.weight(1f)) {
            if (mode == 2) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    lazyItems(pages) { page ->
                        val ratio = if (page.width > 0 && page.height > 0) {
                            page.width.toFloat() / page.height
                        } else 0.7f
                        ZoomablePage(
                            url = client.pageUrl(book.id, page.number),
                            auth = client.authHeader,
                            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                            contentScale = ContentScale.Fit,
                            onZoomChanged = { zoomed = it },
                            onPrevious = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                    )
                                }
                            },
                            onNext = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.lastIndex)
                                    )
                                }
                            },
                            onToggleControls = { showControls = !showControls }
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = mode == 1,
                    userScrollEnabled = !zoomed,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    ZoomablePage(
                        url = client.pageUrl(book.id, pages[index].number),
                        auth = client.authHeader,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onZoomChanged = { zoomed = it },
                        onPrevious = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                )
                            }
                        },
                        onNext = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + 1).coerceAtMost(pages.lastIndex)
                                )
                            }
                        },
                        onToggleControls = { showControls = !showControls }
                    )
                }
            }
        }

        if (showControls) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val current = if (mode == 2) listState.firstVisibleItemIndex else pagerState.currentPage
            Text(
                "第 ${(current + 1).coerceAtMost(pages.size)} / ${pages.size} 页",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Slider(
                value = (current + 1).toFloat(),
                valueRange = 1f..pages.size.toFloat(),
                onValueChange = { value ->
                    val target = value.toInt().coerceIn(1, pages.size) - 1
                    scope.launch {
                        if (mode == 2) listState.scrollToItem(target) else pagerState.scrollToPage(target)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        }
    }
}

@Composable
private fun ReaderModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .background(
                if (selected) Brand else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ZoomablePage(
    url: String,
    auth: String,
    modifier: Modifier,
    contentScale: ContentScale,
    onZoomChanged: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 4f)
                    scale = next
                    offset = if (next > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap ->
                        val third = size.width / 3f
                        when {
                            tap.x < third -> onPrevious()
                            tap.x > third * 2 -> onNext()
                            else -> onToggleControls()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        KomgaImage(
            url = url,
            auth = auth,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = contentScale
        )
    }
}

// ---------- 通用小组件 ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThinProgress(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(3.dp).background(SurfaceHigh, RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Brand, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun KomgaLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Brand)
    }
}

@Composable
private fun KomgaEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun KomgaError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("加载失败：$message", color = Danger, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(999.dp)) { Text("重试") }
        }
    }
}
