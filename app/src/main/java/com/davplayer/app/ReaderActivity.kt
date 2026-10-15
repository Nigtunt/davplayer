package com.davplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PAGE = "page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val startPage = intent.getIntExtra(EXTRA_PAGE, 0)

        val settings = Settings(this)
        if (settings.komgaUrl.isBlank()) { finish(); return }
        val client = KomgaClient(settings.komgaUrl, settings.komgaUser, settings.komgaPass)

        setContent {
            // 阅读器始终深色
            MaterialTheme(colorScheme = DavTheme.DarkColors) {
                ReaderScreen(
                    client = client,
                    bookId = bookId,
                    title = title,
                    startPage = startPage,
                    onBack = { finish() }
                )
            }
        }
    }
}

enum class ReadMode(val label: String) {
    LTR("左 → 右"), RTL("右 → 左"), VERTICAL("竖向滚动")
}

@Composable
fun ReaderScreen(
    client: KomgaClient,
    bookId: String,
    title: String,
    startPage: Int,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = remember(client) {
        ImageLoader.Builder(ctx).okHttpClient(client.http).crossfade(false).build()
    }

    var pages by remember { mutableStateOf<List<KomgaPage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(ReadMode.LTR) }
    val controlsVisible = remember { mutableStateOf(false) }
    val currentPage = remember { mutableStateOf(0) }   // 0-based

    LaunchedEffect(bookId) {
        try {
            pages = client.bookPages(bookId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    // 进度同步：停留 1.2s 后上报 Komga（page 为 1-based）
    suspend fun syncProgress(pageIndex: Int, pageCount: Int) {
        runCatching {
            client.markReadProgress(
                bookId,
                page = pageIndex + 1,
                completed = pageIndex >= pageCount - 1
            )
        }
    }

    LaunchedEffect(currentPage.value, pages?.size) {
        val p = pages ?: return@LaunchedEffect
        if (p.isEmpty()) return@LaunchedEffect
        delay(1200)
        syncProgress(currentPage.value, p.size)
    }

    DisposableEffect(Unit) {
        onDispose {
            val p = pages
            if (p != null && p.isNotEmpty()) {
                // 退出时也同步一次
                kotlinx.coroutines.runBlocking {
                    runCatching { client.markReadProgress(bookId, currentPage.value + 1, currentPage.value >= p.size - 1) }
                }
            }
        }
    }

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败：$error", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onBack) { Text("返回") }
                    }
                }
                pages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                pages!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这本书没有页面", color = Color.White)
                }
                else -> {
                    val pageList = pages!!
                    val pageCount = pageList.size

                    if (mode == ReadMode.VERTICAL) {
                        val listState = rememberLazyListState()
                        // 首次定位
                        LaunchedEffect(Unit) {
                            if (startPage > 0) listState.scrollToItem((startPage - 1).coerceIn(0, pageCount - 1))
                        }
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest {
                                currentPage.value = it
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { controlsVisible.value = !controlsVisible.value }
                        ) {
                            items(pageCount, key = { it }) { i ->
                                ReaderPage(client, bookId, pageList[i].number, ContentScale.FillWidth)
                            }
                        }
                    } else {
                        val pagerState = rememberPagerState(
                            initialPage = (startPage - 1).coerceIn(0, pageCount - 1)
                        ) { pageCount }
                        val zoomed = remember { mutableStateOf(false) }
                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.settledPage }.collectLatest {
                                currentPage.value = it
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = mode == ReadMode.RTL,
                            userScrollEnabled = !zoomed.value,
                            modifier = Modifier.fillMaxSize()
                        ) { index ->
                            PagerPage(
                                client = client,
                                bookId = bookId,
                                pageNumber = pageList[index].number,
                                zoomed = zoomed,
                                onTapZone = { zone ->
                                    when (zone) {
                                        0 -> { // 左区
                                            scope.launch {
                                                val target = if (mode == ReadMode.RTL) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                                if (target in 0 until pageCount) pagerState.animateScrollToPage(target)
                                                else controlsVisible.value = !controlsVisible.value
                                            }
                                        }
                                        2 -> { // 右区
                                            scope.launch {
                                                val target = if (mode == ReadMode.RTL) pagerState.currentPage - 1 else pagerState.currentPage + 1
                                                if (target in 0 until pageCount) pagerState.animateScrollToPage(target)
                                                else controlsVisible.value = !controlsVisible.value
                                            }
                                        }
                                        else -> controlsVisible.value = !controlsVisible.value
                                    }
                                }
                            )
                        }
                        // 预取下一页
                        LaunchedEffect(currentPage.value) {
                            val next = currentPage.value + 1
                            if (next < pageCount) {
                                imageLoader.enqueue(
                                    ImageRequest.Builder(ctx)
                                        .data(client.pageUrl(bookId, pageList[next].number))
                                        .build()
                                )
                            }
                        }
                    }

                    // 顶部栏
                    AnimatedVisibility(
                        visible = controlsVisible.value,
                        enter = fadeIn(), exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .statusBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                            }
                            Text(
                                title, color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                mode = when (mode) {
                                    ReadMode.LTR -> ReadMode.RTL
                                    ReadMode.RTL -> ReadMode.VERTICAL
                                    ReadMode.VERTICAL -> ReadMode.LTR
                                }
                            }) {
                                Text(mode.label, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // 底部：页码 + 跳转
                    AnimatedVisibility(
                        visible = controlsVisible.value,
                        enter = fadeIn(), exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .navigationBarsPadding()
                        ) {
                            Text(
                                "第 ${currentPage.value + 1} / $pageCount 页",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 横滑模式的单页：支持双指缩放，左/中/右点击区 */
@Composable
private fun PagerPage(
    client: KomgaClient,
    bookId: String,
    pageNumber: Int,
    zoomed: MutableState<Boolean>,
    onTapZone: (Int) -> Unit  // 0=左 1=中 2=右
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    zoomed.value = newScale > 1.02f
                    offset = if (zoomed.value) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero; zoomed.value = false }
                        else { scale = 2f; zoomed.value = true }
                    },
                    onTap = { p ->
                        val zone = when {
                            p.x < size.width * 0.3f -> 0
                            p.x > size.width * 0.7f -> 2
                            else -> 1
                        }
                        onTapZone(zone)
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(client.pageUrl(bookId, pageNumber))
                .build(),
            contentDescription = "第 $pageNumber 页",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 竖向滚动模式的单页 */
@Composable
private fun ReaderPage(client: KomgaClient, bookId: String, pageNumber: Int, scale: ContentScale) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(client.pageUrl(bookId, pageNumber))
            .build(),
        contentDescription = "第 $pageNumber 页",
        contentScale = scale,
        modifier = Modifier.fillMaxWidth()
    )
}
