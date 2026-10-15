package com.davplayer.app

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.roundToInt

class ReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SERIES = "seriesTitle"
        const val EXTRA_PAGE = "page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES) ?: ""
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
                    seriesTitle = seriesTitle,
                    startPage = startPage,
                    onBack = { finish() }
                )
            }
        }
    }
}

fun startReader(ctx: Context, bookId: String, title: String, page: Int = 0, seriesTitle: String = "") {
    ctx.startActivity(Intent(ctx, ReaderActivity::class.java).apply {
        putExtra(ReaderActivity.EXTRA_BOOK_ID, bookId)
        putExtra(ReaderActivity.EXTRA_TITLE, title)
        putExtra(ReaderActivity.EXTRA_SERIES, seriesTitle)
        putExtra(ReaderActivity.EXTRA_PAGE, page)
    })
}

enum class ReadMode(val label: String) {
    LTR("左 → 右"), RTL("右 → 左"), VERTICAL("竖向滚动")
}

@Composable
fun ReaderScreen(
    client: KomgaClient,
    bookId: String,
    title: String,
    seriesTitle: String,
    startPage: Int,
    onBack: () -> Unit
) {
    var pages by remember { mutableStateOf<List<KomgaPage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) {
        try {
            pages = client.bookPages(bookId)
        } catch (e: Exception) {
            error = e.message
        }
    }

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
            else -> ReaderContent(
                client = client,
                bookId = bookId,
                title = title,
                seriesTitle = seriesTitle,
                pages = pages!!,
                startPage = startPage,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ReaderContent(
    client: KomgaClient,
    bookId: String,
    title: String,
    seriesTitle: String,
    pages: List<KomgaPage>,
    startPage: Int,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyStore = remember { ComicHistoryStore(ctx) }
    val imageLoader = remember(client) { buildKomgaImageLoader(ctx, client) }

    val pageCount = pages.size
    var mode by remember { mutableStateOf(ReadMode.LTR) }
    val controlsVisible = remember { mutableStateOf(false) }
    val currentPage = remember { mutableStateOf((startPage - 1).coerceIn(0, pageCount - 1)) } // 0-based
    var sliderDrag by remember { mutableStateOf<Float?>(null) }

    val pagerState = rememberPagerState(initialPage = currentPage.value) { pageCount }
    val listState = rememberLazyListState()

    // 模式切换时保持页码
    LaunchedEffect(mode) {
        if (mode == ReadMode.VERTICAL) listState.scrollToItem(currentPage.value)
        else pagerState.scrollToPage(currentPage.value)
    }

    // 跟踪当前页
    LaunchedEffect(pagerState, mode) {
        if (mode != ReadMode.VERTICAL) {
            snapshotFlow { pagerState.settledPage }.collectLatest { currentPage.value = it }
        }
    }
    LaunchedEffect(listState, mode) {
        if (mode == ReadMode.VERTICAL) {
            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { currentPage.value = it }
        }
    }

    val scrolling = if (mode == ReadMode.VERTICAL) listState.isScrollInProgress
    else pagerState.isScrollInProgress

    // 进度同步：Komga 云端 + 本地漫画历史（停留 1.2s 后记录）
    fun saveProgress(pageIndex: Int) {
        val page = pageIndex + 1
        historyStore.upsert(
            ComicHistoryItem(
                bookId = bookId,
                seriesTitle = seriesTitle,
                title = title,
                page = page,
                pagesCount = pageCount,
                updatedAt = System.currentTimeMillis()
            )
        )
        scope.launch {
            runCatching {
                client.markReadProgress(bookId, page = page, completed = pageIndex >= pageCount - 1)
            }
        }
    }

    LaunchedEffect(currentPage.value) {
        delay(1200)
        saveProgress(currentPage.value)
    }
    DisposableEffect(Unit) {
        onDispose { saveProgress(currentPage.value) }
    }

    fun jumpTo(index: Int) {
        val target = index.coerceIn(0, pageCount - 1)
        currentPage.value = target
        scope.launch {
            if (mode == ReadMode.VERTICAL) listState.scrollToItem(target)
            else pagerState.scrollToPage(target)
        }
    }

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {

            // -------- 阅读内容 --------
            if (mode == ReadMode.VERTICAL) {
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
                        AsyncImage(
                            model = SecureImage(client.pageUrl(bookId, pages[i].number)),
                            contentDescription = "第 ${i + 1} 页",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                val zoomed = remember { mutableStateOf(false) }
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = mode == ReadMode.RTL,
                    userScrollEnabled = !zoomed.value,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    PagerPage(
                        client = client,
                        bookId = bookId,
                        pageNumber = pages[index].number,
                        zoomed = zoomed,
                        onTapZone = { zone ->
                            when (zone) {
                                0 -> {
                                    val target = if (mode == ReadMode.RTL) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                    if (target in 0 until pageCount) scope.launch { pagerState.animateScrollToPage(target) }
                                    else controlsVisible.value = !controlsVisible.value
                                }
                                2 -> {
                                    val target = if (mode == ReadMode.RTL) pagerState.currentPage - 1 else pagerState.currentPage + 1
                                    if (target in 0 until pageCount) scope.launch { pagerState.animateScrollToPage(target) }
                                    else controlsVisible.value = !controlsVisible.value
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
                                .data(SecureImage(client.pageUrl(bookId, pages[next].number)))
                                .build()
                        )
                    }
                }
            }

            // -------- 滑动中的页数提示 --------
            if (scrolling && !controlsVisible.value) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "第 ${currentPage.value + 1} / $pageCount 页",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            // -------- 顶部栏 --------
            AnimatedVisibility(
                visible = controlsVisible.value,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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

            // -------- 底部：可拖动进度条（拖动只变数字，松手跳转） --------
            AnimatedVisibility(
                visible = controlsVisible.value,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "第 ${(sliderDrag?.roundToInt() ?: (currentPage.value + 1))} / $pageCount 页",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sliderDrag ?: (currentPage.value + 1).toFloat(),
                        valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                        onValueChange = { sliderDrag = it },        // 拖动中只更新数字
                        onValueChangeFinished = {                    // 松手才跳转
                            sliderDrag?.let { jumpTo(it.roundToInt() - 1) }
                            sliderDrag = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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
            model = SecureImage(client.pageUrl(bookId, pageNumber)),
            contentDescription = "第 $pageNumber 页",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
