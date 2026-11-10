package com.davplayer.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.URLDecoder

@OptIn(UnstableApi::class)
class VideoDetailActivity : ComponentActivity() {
    companion object {
        const val EXTRA_HREF = "href"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSITION = "pos"
        const val EXTRA_SUBTITLE_HREF = "subtitle_href"
        const val EXTRA_SUBTITLE_NAME = "subtitle_name"
        const val EXTRA_SUBTITLE_SIZE = "subtitle_size"
        const val EXTRA_SIZE = "size"
        const val EXTRA_LAST_MODIFIED = "last_modified"
    }

    private var player: ExoPlayer? = null
    private var davClient: WebDavClient? = null
    private var videoUrl = ""
    private val fullscreen = mutableStateOf(false)
    private val embeddedGroups = mutableStateOf<List<Tracks.Group>>(emptyList())
    private val subtitleHref = mutableStateOf<String?>(null)
    private val subtitleName = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        val startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        subtitleHref.value = intent.getStringExtra(EXTRA_SUBTITLE_HREF)
        subtitleName.value = intent.getStringExtra(EXTRA_SUBTITLE_NAME)
        val subtitleSize = intent.getLongExtra(EXTRA_SUBTITLE_SIZE, 0L)
        val size = intent.getLongExtra(EXTRA_SIZE, 0L)
        val lastModified = intent.getLongExtra(EXTRA_LAST_MODIFIED, 0L)
        val settings = Settings(this)
        val source = settings.activeSource()
        val unsupported = isUnsupportedVideo(title.ifBlank { href })

        val exo = if (source == null || unsupported) null else runCatching {
            val client = WebDavClient(source.url, source.user, source.pass)
            davClient = client
            videoUrl = client.fileUrl(href)
            val headers = client.authHeaders()
            val dataSource = OkHttpDataSource.Factory(
                OkHttpClient.Builder().addInterceptor(HttpLogInterceptor()).build()
            ).setDefaultRequestProperties(headers)
            val sub = subtitleHref.value?.let { subHref ->
                MediaItem.SubtitleConfiguration.Builder(
                    android.net.Uri.parse(client.fileUrl(subHref))
                )
                    .setMimeType(subtitleMime(subHref))
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            val item = MediaItem.Builder()
                .setUri(videoUrl)
                .apply { if (sub != null) setSubtitleConfigurations(listOf(sub)) }
                .build()
            ExoPlayer.Builder(this)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 45_000, 1_500, 3_000)
                        .build()
                )
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onTracksChanged(tracks: Tracks) {
                            embeddedGroups.value = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        }
                    })
                    setMediaItem(item)
                    prepare()
                    if (startPosition > 0) seekTo(startPosition)
                    playWhenReady = true
                }
        }.getOrNull()
        player = exo

        setContent {
            DavThemeProvider(dark = resolveDark(settings.themeMode)) {
                VideoDetailScreen(
                    title = title,
                    href = href,
                    size = size,
                    lastModified = lastModified,
                    subtitleHref = subtitleHref.value,
                    subtitleName = subtitleName.value,
                    subtitleSize = subtitleSize,
                    player = exo,
                    error = when {
                        source == null -> "没有可用的 WebDAV 源"
                        unsupported -> unsupportedMessage(title.ifBlank { href })
                        exo == null -> "播放器初始化失败"
                        else -> null
                    },
                    client = davClient,
                    embedded = embeddedGroups.value,
                    subtitlesEnabled = embeddedGroups.value.any { it.isSelected } || subtitleHref.value != null,
                    fullscreen = fullscreen.value,
                    onBack = {
                        if (fullscreen.value) setPlayerFullscreen(false) else finish()
                    },
                    onToggleFullscreen = { setPlayerFullscreen(!fullscreen.value) },
                    onPickSubtitle = { entry -> loadSubtitle(entry) },
                    onSelectEmbedded = { group ->
                        player?.let {
                            applyTextTrack(it, group)
                            subtitleName.value = subtitleLabel(group.getTrackFormat(0))
                            subtitleHref.value = null
                        }
                    },
                    onDisableSubtitles = {
                        player?.let {
                            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                        }
                        subtitleName.value = null
                        subtitleHref.value = null
                    }
                )
            }
        }
    }

    private fun loadSubtitle(entry: DavEntry) {
        val exo = player ?: return
        val client = davClient ?: return
        val wasPlaying = exo.isPlaying
        val position = exo.currentPosition.coerceAtLeast(0)
        val sub = MediaItem.SubtitleConfiguration.Builder(
            android.net.Uri.parse(client.fileUrl(entry.href))
        )
            .setMimeType(subtitleMime(entry.name))
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val item = MediaItem.Builder()
            .setUri(videoUrl)
            .setSubtitleConfigurations(listOf(sub))
            .build()
        subtitleHref.value = entry.href
        subtitleName.value = entry.name
        exo.setMediaItem(item, position)
        exo.prepare()
        exo.playWhenReady = wasPlaying
    }

    private fun setPlayerFullscreen(fs: Boolean) {
        fullscreen.value = fs
        if (fs) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            applyImmersive(true)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            applyImmersive(false)
        }
    }

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

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun VideoDetailScreen(
    title: String,
    href: String,
    size: Long,
    lastModified: Long,
    subtitleHref: String?,
    subtitleName: String?,
    subtitleSize: Long,
    player: ExoPlayer?,
    error: String?,
    client: WebDavClient?,
    embedded: List<Tracks.Group>,
    subtitlesEnabled: Boolean,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPickSubtitle: (DavEntry) -> Unit,
    onSelectEmbedded: (Tracks.Group) -> Unit,
    onDisableSubtitles: () -> Unit
) {
    var showSubtitlePicker by remember { mutableStateOf(false) }
    val meta = parseVideoMeta(title)

    if (fullscreen) {
        BackHandler { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (player != null && error == null) {
                InlineVideoPlayer(
                    player = player,
                    title = title,
                    quality = meta.labels.firstOrNull(),
                    fullscreen = true,
                    subtitleName = subtitleName,
                    onBack = onBack,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(error ?: "无法播放", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            if (player != null && error == null) {
                InlineVideoPlayer(
                    player = player,
                    title = title,
                    quality = meta.labels.firstOrNull(),
                    fullscreen = false,
                    subtitleName = subtitleName,
                    onBack = onBack,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF25314C)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        error ?: "无法播放",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    buildString {
                        append(if (size > 0) formatSize(size) else "视频")
                        formatDavModified(lastModified)?.let { append(" · $it") }
                    },
                    fontSize = 11.sp,
                    color = davExtra().textMuted
                )
                Spacer(Modifier.height(9.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.labels.forEach { label ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                DetailCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "轨道与外挂字幕",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showSubtitlePicker = true }) {
                            Text("选择字幕", fontSize = 11.sp)
                        }
                    }

                    if (subtitleHref != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { showSubtitlePicker = true }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Subtitles, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    subtitleName ?: subtitleHref.substringAfterLast('/'),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        append(subtitleHref.substringAfterLast('.', "").uppercase())
                                        if (subtitleSize > 0) append(" · ${formatSize(subtitleSize)}")
                                        append(" · 外挂")
                                    },
                                    fontSize = 9.sp,
                                    color = davExtra().textMuted
                                )
                            }
                            Text("已载入", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (embedded.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("内嵌字幕", fontSize = 10.sp, color = davExtra().textMuted)
                        embedded.forEach { group ->
                            val format = group.getTrackFormat(0)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectEmbedded(group) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Subtitles, null,
                                    tint = if (group.isSelected) MaterialTheme.colorScheme.primary
                                    else davExtra().textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        subtitleLabel(format),
                                        fontSize = 11.sp,
                                        color = if (group.isSupported) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!group.isSupported) {
                                        Text(
                                            "图形字幕，内置播放器无法渲染",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (group.isSelected) {
                                    Text("当前", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    if (subtitleHref == null && embedded.isEmpty()) {
                        Text(
                            "没有内嵌字幕，也没有找到同名外挂字幕。可以点「选择字幕」从其他目录选。",
                            fontSize = 10.sp,
                            color = davExtra().textMuted
                        )
                    }

                    if (subtitlesEnabled || subtitleHref != null) {
                        TextButton(
                            onClick = onDisableSubtitles,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("关闭字幕", fontSize = 10.sp) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                DetailCard {
                    Text("文件信息", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    DetailLine("类型", "视频")
                    DetailLine("大小", if (size > 0) formatSize(size) else "未知")
                    formatDavModified(lastModified)?.let { DetailLine("修改时间", it) }
                    DetailLine("路径", prettyDavPath(href))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSubtitlePicker && client != null) {
        SubtitlePickerDialog(
            client = client,
            startDir = parentDirOf(href),
            embedded = embedded,
            subtitlesEnabled = subtitlesEnabled,
            onDismiss = { showSubtitlePicker = false },
            onDisableSubtitles = {
                showSubtitlePicker = false
                onDisableSubtitles()
            },
            onSelectEmbedded = { index ->
                showSubtitlePicker = false
                embedded.getOrNull(index)?.let(onSelectEmbedded)
            },
            onPick = {
                showSubtitlePicker = false
                onPickSubtitle(it)
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InlineVideoPlayer(
    player: ExoPlayer,
    title: String,
    quality: String?,
    fullscreen: Boolean,
    subtitleName: String?,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var dragValue by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0)
            }

            override fun onPlayerError(error: PlaybackException) {
                playing = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            quality?.let {
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        it,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .size(if (fullscreen) 62.dp else 50.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable {
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(if (fullscreen) 32.dp else 26.dp)
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            val dur = duration.coerceAtLeast(1L)
            Slider(
                value = dragValue ?: position.toFloat().coerceIn(0f, dur.toFloat()),
                valueRange = 0f..dur.toFloat(),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { player.seekTo(it.toLong()) }
                    dragValue = null
                },
                modifier = Modifier.fillMaxWidth().height(22.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMs(dragValue?.toLong() ?: position)} / ${formatMs(duration)}",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                if (!subtitleName.isNullOrBlank()) {
                    Text("中字", color = Color(0xFFFF6B6B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen, if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(13.dp),
        content = content
    )
}

private fun prettyDavPath(href: String): String =
    href.split('/').joinToString("/") { segment ->
        runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
    }

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            color = davExtra().textMuted,
            modifier = Modifier.width(58.dp)
        )
        Text(
            value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
