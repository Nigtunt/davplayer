package com.davplayer.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.FileOutputStream

val SUBTITLE_EXTS = setOf("srt", "ass", "ssa", "vtt")

fun isSubtitleFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTS

/** 持有播放器渲染画面引用，用于抽帧做封面 */
class SurfaceRefs {
    var frame: AspectRatioFrameLayout? = null
    var texture: TextureView? = null
}

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HREF = "href"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSITION = "pos"
    }

    private var player: ExoPlayer? = null
    private val surfaceRefs = SurfaceRefs()
    private var fullscreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 进来时手机已经是横屏（例如先横过来再点视频）：直接沉浸式
        val startLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        fullscreen.value = startLandscape
        applyImmersive(startLandscape)

        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        val startPos = intent.getLongExtra(EXTRA_POSITION, 0L)

        val settings = Settings(this)
        val store = HistoryStore(this)
        val source = settings.activeSource() ?: run { finish(); return }
        val client = WebDavClient(source.url, source.user, source.pass)
        val url = client.fileUrl(href)
        val headers = client.authHeaders()

        // RMVB / WMV 等容器 Media3 没有对应解封装器，先给出明确提示，
        // 不再发无意义的请求，避免界面一直卡在缓冲转圈
        val unsupported = isUnsupportedVideo(title.ifBlank { href })

        val dataSourceFactory = OkHttpDataSource.Factory(
            OkHttpClient.Builder().addInterceptor(HttpLogInterceptor()).build()
        ).setDefaultRequestProperties(headers)

        // 解码模式：软件优先可修复部分视频（如 10bit H.264）硬解卡顿
        val renderersFactory = DefaultRenderersFactory(this).apply {
            if (settings.decodeMode == "sw") {
                setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                    MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                        .sortedByDescending { it.softwareOnly }
                }
            }
        }
        // 加大缓冲，对抗 WebDAV 网络抖动
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .build()

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exo
        if (!unsupported) {
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            if (startPos > 0) exo.seekTo(startPos)
            exo.playWhenReady = true
        }

        setContent {
            DavThemeProvider(dark = true) {
                PlayerScreen(
                    player = exo,
                    client = client,
                    videoHref = href,
                    videoUrl = url,
                    title = title,
                    settings = settings,
                    surfaceRefs = surfaceRefs,
                    fullscreen = fullscreen,
                    initialError = if (unsupported) unsupportedMessage(title.ifBlank { href }) else null,
                    canRetry = !unsupported,
                    onOpenExternal = {
                        if (!openWithExternalPlayer(this, client.fileUrlWithCredentials(href), title)) {
                            android.widget.Toast.makeText(
                                this, "没有找到可用的外部播放器", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBack = { finish() },
                    saveHistory = {
                        val dur = exo.duration
                        if (dur > 0) {
                            var pos = exo.currentPosition
                            if (dur - pos < 8000) pos = 0 // 快播完则下次从头开始
                            store.upsert(HistoryItem(href, title, pos, dur, System.currentTimeMillis()))
                        }
                    },
                    ensureCover = {
                        // 直接从渲染画面截图：仅在开始播放后触发，未播放不产生任何请求或缓存
                        val f = store.coverFile(href)
                        if (!f.exists()) {
                            val bmp = surfaceRefs.texture?.bitmap
                            if (bmp != null) {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        FileOutputStream(f).use {
                                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    /** 只负责系统栏的显隐（沉浸式），不改变屏幕方向 */
    private fun applyImmersive(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 物理旋转手机：横屏自动进入全屏（隐藏状态栏/导航栏），转回竖屏恢复
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        fullscreen.value = landscape
        applyImmersive(landscape)
    }

    fun setFullscreen(fs: Boolean) {
        fullscreen.value = fs
        if (fs) {
            // 点全屏按钮：锁横屏 + 沉浸式
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            applyImmersive(true)
        } else {
            // 退出全屏：解除方向锁，恢复系统栏（当前是横屏就保持横屏）
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            applyImmersive(false)
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    client: WebDavClient,
    videoHref: String,
    videoUrl: String,
    title: String,
    settings: Settings,
    surfaceRefs: SurfaceRefs,
    fullscreen: State<Boolean>,
    initialError: String? = null,
    canRetry: Boolean = true,
    onOpenExternal: () -> Unit = {},
    onBack: () -> Unit,
    saveHistory: () -> Unit,
    ensureCover: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as PlayerActivity

    val errorMsg = remember { mutableStateOf(initialError) }
    val isPlaying = remember { mutableStateOf(false) }
    val isBuffering = remember { mutableStateOf(initialError == null) }
    val positionMs = remember { mutableStateOf(0L) }
    val durationMs = remember { mutableStateOf(0L) }
    val controlsVisible = remember { mutableStateOf(true) }
    val boosting = remember { mutableStateOf(false) }
    val baseSpeed = remember { mutableStateOf(1f) }
    val speedPresets = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f) }
    val subtitleName = remember { mutableStateOf<String?>(null) }
    val showSubtitlePicker = remember { mutableStateOf(false) }

    var coverTried by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                if (playing && !coverTried) {
                    coverTried = true
                    scope.launch {
                        delay(2500) // 播一小段再截图，封面更有代表性
                        ensureCover()
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (errorMsg.value == null) isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs.value = player.duration.coerceAtLeast(0)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 出错了就停下来给出原因，不再无限转圈
                AppLog.log("PLAYER", "播放失败 ${error.errorCodeName}: ${error.message}")
                isBuffering.value = false
                errorMsg.value = describePlaybackError(error)
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    surfaceRefs.frame?.setAspectRatio(
                        size.width * size.pixelWidthHeightRatio / size.height
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            saveHistory()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            positionMs.value = player.currentPosition.coerceAtLeast(0)
            durationMs.value = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (player.isPlaying) saveHistory()
        }
    }
    LaunchedEffect(controlsVisible.value, isPlaying.value) {
        if (controlsVisible.value && isPlaying.value) {
            delay(4000)
            controlsVisible.value = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // TextureView 渲染画面（支持直接截图做封面）
        AndroidView(
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    setAspectRatio(16f / 9f)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val tv = TextureView(ctx)
                    addView(tv, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    player.setVideoTextureView(tv)
                    surfaceRefs.frame = this
                    surfaceRefs.texture = tv
                }
            },
            modifier = Modifier.align(Alignment.Center).fillMaxSize()
        )

        // 手势层：点击切换控制栏，长按临时倍速，横向拖动调整进度
        val seekPreview = remember { mutableStateOf<Long?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible.value = !controlsVisible.value },
                        onLongPress = {
                            boosting.value = true
                            player.setPlaybackSpeed(settings.longPressSpeed)
                        },
                        onPress = {
                            // 长按被打断（松手或开始拖动）时恢复速度
                            awaitRelease()
                            if (boosting.value) {
                                player.setPlaybackSpeed(baseSpeed.value)
                                boosting.value = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var targetPos = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            targetPos = player.currentPosition
                            seekPreview.value = targetPos
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val dur = player.duration
                            if (dur > 0) {
                                // 拖满一屏宽度 ≈ 前进/后退 120 秒
                                val deltaMs = (dragAmount / size.width * 120_000).toLong()
                                targetPos = (targetPos + deltaMs).coerceIn(0, dur)
                                seekPreview.value = targetPos
                            }
                        },
                        onDragEnd = {
                            player.seekTo(targetPos)
                            seekPreview.value = null
                        },
                        onDragCancel = { seekPreview.value = null }
                    )
                }
        )

        // 拖动进度预览
        seekPreview.value?.let { target ->
            val dur = durationMs.value
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xEB0F0F0F),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    "${formatMs(target)} / ${formatMs(dur)}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (isBuffering.value) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        if (boosting.value) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    "⚡ ${"%.1f".format(settings.longPressSpeed)}x 播放中",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White
                )
            }
        }

        // 顶部栏
        AnimatedVisibility(
            visible = controlsVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    // 沉浸式横屏时避开挖孔/摄像头区域，返回键不会被挡住
                    .displayCutoutPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                subtitleName.value?.let {
                    Text(
                        "字幕: $it",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 底部控制栏
        AnimatedVisibility(
            visible = controlsVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(top = 28.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp)
            ) {
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableStateOf(0f) }
                val dur = durationMs.value.coerceAtLeast(0)
                val sliderValue = if (dragging) dragValue
                else positionMs.value.toFloat().coerceIn(0f, dur.toFloat().coerceAtLeast(1f))

                // 品牌渐变进度条 + 白色圆点滑块
                Slider(
                    value = sliderValue,
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = {
                        player.seekTo(dragValue.toLong())
                        dragging = false
                    },
                    thumb = {
                        Box(
                            Modifier
                                .size(13.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    },
                    track = { sliderState ->
                        val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                        val fraction = if (range > 0f)
                            ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                        else 0f
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.28f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(DavTheme.BrandGradient)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(
                            if (isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "播放/暂停",
                            tint = Color.White
                        )
                    }
                    Text(
                        "${formatMs(if (dragging) dragValue.toLong() else positionMs.value)} / ${formatMs(dur)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                    // 字幕
                    IconButton(onClick = { showSubtitlePicker.value = true }) {
                        Icon(Icons.Default.Subtitles, "选择字幕", tint = Color.White)
                    }
                    // 倍速胶囊
                    Surface(
                        onClick = {
                            val cur = baseSpeed.value
                            val idx = speedPresets.indexOfFirst { it >= cur - 0.001f }
                            val next = speedPresets[(idx + 1) % speedPresets.size]
                            baseSpeed.value = next
                            if (!boosting.value) player.setPlaybackSpeed(next)
                        },
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            "${"%.2g".format(baseSpeed.value)}x",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    // 全屏
                    IconButton(onClick = { activity.setFullscreen(!fullscreen.value) }) {
                        Icon(
                            if (fullscreen.value) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            "全屏",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 播放失败提示：显示具体原因 + 重试 / 外部播放器 / 返回
        errorMsg.value?.let { msg ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "无法播放", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        msg,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canRetry) {
                            Button(
                                onClick = {
                                    errorMsg.value = null
                                    isBuffering.value = true
                                    player.prepare()
                                    player.playWhenReady = true
                                },
                                shape = RoundedCornerShape(999.dp)
                            ) { Text("重试") }
                        }
                        OutlinedButton(onClick = onOpenExternal, shape = RoundedCornerShape(999.dp)) {
                            Text("外部播放器", color = Color.White)
                        }
                        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(999.dp)) {
                            Text("返回", color = Color.White)
                        }
                    }
                }
            }
        }

        // 字幕选择对话框（默认打开视频所在同级目录）
        if (showSubtitlePicker.value) {
            SubtitlePickerDialog(
                client = client,
                startDir = parentDirOf(videoHref),
                onDismiss = { showSubtitlePicker.value = false },
                onPick = { entry ->
                    showSubtitlePicker.value = false
                    subtitleName.value = entry.name
                    val mime = when (entry.name.substringAfterLast('.', "").lowercase()) {
                        "srt" -> "application/x-subrip"
                        "vtt" -> "text/vtt"
                        "ass", "ssa" -> "text/x-ssa"
                        else -> "application/x-subrip"
                    }
                    val sub = MediaItem.SubtitleConfiguration.Builder(
                        Uri.parse(client.fileUrl(entry.href))
                    )
                        .setMimeType(mime)
                        .setLanguage("und")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    val pos = player.currentPosition
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setUri(videoUrl)
                            .setSubtitleConfigurations(listOf(sub))
                            .build(),
                        pos
                    )
                    player.prepare()
                    player.play()
                }
            )
        }
    }
}

fun parentDirOf(href: String): String {
    val t = href.trimEnd('/')
    val parent = t.substringBeforeLast('/', "")
    return if (parent.isEmpty()) "/" else "$parent/"
}

@Composable
fun SubtitlePickerDialog(
    client: WebDavClient,
    startDir: String,
    onDismiss: () -> Unit,
    onPick: (DavEntry) -> Unit
) {
    var dir by remember { mutableStateOf(if (startDir.startsWith(client.rootPath)) startDir else client.rootPath) }
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(dir) {
        loading = true; error = null
        try {
            entries = client.list(dir)
        } catch (e: Exception) {
            error = e.message
            entries = null
        }
        loading = false
    }

    fun parentOf(p: String): String {
        val parent = parentDirOf(p)
        return if (parent.length < client.rootPath.length) client.rootPath else parent
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("选择字幕文件")
                Text(
                    decodePathForUi(dir),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().height(380.dp)) {
                // 上级目录
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = dir != client.rootPath) { dir = parentOf(dir) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder, null,
                        tint = if (dir != client.rootPath) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(".. (上级目录)")
                }
                HorizontalDivider()
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        val visible = entries.orEmpty().filter { it.isDir || isSubtitleFile(it.name) }
                        if (visible.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("此目录没有字幕文件（srt/ass/ssa/vtt）")
                            }
                        } else {
                            LazyColumn {
                                items(visible, key = { it.href }) { entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (entry.isDir) {
                                                    dir = if (entry.href.endsWith("/")) entry.href else entry.href + "/"
                                                } else onPick(entry)
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (entry.isDir) Icons.Default.Folder else Icons.Default.Subtitles,
                                            null,
                                            tint = if (entry.isDir) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun decodePathForUi(path: String): String = runCatching {
    java.net.URLDecoder.decode(path, "UTF-8")
}.getOrDefault(path)

// ==================== 播放失败处理 ====================

/** 已知不支持的容器格式提示文案 */
fun unsupportedMessage(name: String): String {
    val ext = name.substringAfterLast('.', "").uppercase().ifBlank { "该" }
    return "内置播放器不支持 $ext 格式（Media3 没有对应的解封装器），无法播放。\n\n" +
            "可点下方「外部播放器」用手机里已安装的播放器打开（如 VLC / MX Player），" +
            "或先把视频转码成 MP4 / MKV 再播放。"
}

/** 用系统里已安装的播放器打开；外部播放器读不到认证头，凭据已拼进地址 */
fun openWithExternalPlayer(ctx: Context, url: String, title: String): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    ctx.startActivity(Intent.createChooser(intent, "用其他播放器打开"))
    true
}.getOrDefault(false)

/** 把 PlaybackException 翻译成人能看懂的原因 */
fun describePlaybackError(e: PlaybackException): String {
    val detail = (e.cause?.message ?: e.message ?: "").take(240)
    val head = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "服务器返回错误：文件不存在或没有访问权限（401 / 403 / 404）。"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "网络连接失败，请检查 WebDAV 地址与当前网络。"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
            "视频容器不受支持或文件已损坏。"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            "视频编码不受支持，可在「我的 → 解码方式」切换为软件优先后重试。"
        else -> "播放失败（${e.errorCodeName}）。"
    }
    return if (detail.isBlank()) head else "$head\n\n$detail"
}
