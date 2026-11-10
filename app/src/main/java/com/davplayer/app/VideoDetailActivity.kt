package com.davplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.net.URLDecoder
import kotlinx.coroutines.delay

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        val startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        val subtitleHref = intent.getStringExtra(EXTRA_SUBTITLE_HREF)
        val subtitleName = intent.getStringExtra(EXTRA_SUBTITLE_NAME)
        val subtitleSize = intent.getLongExtra(EXTRA_SUBTITLE_SIZE, 0L)
        val size = intent.getLongExtra(EXTRA_SIZE, 0L)
        val lastModified = intent.getLongExtra(EXTRA_LAST_MODIFIED, 0L)
        val settings = Settings(this)
        val source = settings.activeSource()
        val unsupported = isUnsupportedVideo(title.ifBlank { href })

        val exo = if (source == null || unsupported) null else runCatching {
            val client = WebDavClient(source.url, source.user, source.pass)
            val headers = client.authHeaders()
            val dataSource = OkHttpDataSource.Factory(
                OkHttpClient.Builder().addInterceptor(HttpLogInterceptor()).build()
            ).setDefaultRequestProperties(headers)
            val subConfig = subtitleHref?.let {
                MediaItem.SubtitleConfiguration.Builder(
                    android.net.Uri.parse(client.fileUrl(it))
                )
                    .setMimeType(subtitleMime(it))
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            val item = MediaItem.Builder()
                .setUri(client.fileUrl(href))
                .apply { if (subConfig != null) setSubtitleConfigurations(listOf(subConfig)) }
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
                    subtitleHref = subtitleHref,
                    subtitleName = subtitleName,
                    subtitleSize = subtitleSize,
                    player = exo,
                    error = when {
                        source == null -> "没有可用的 WebDAV 源"
                        unsupported -> unsupportedMessage(title.ifBlank { href })
                        exo == null -> "播放器初始化失败"
                        else -> null
                    },
                    onBack = { finish() },
                    onFullscreen = { position ->
                        player?.pause()
                        startPlayer(this, href, title, position, subtitleHref)
                    }
                )
            }
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
    onBack: () -> Unit,
    onFullscreen: (Long) -> Unit
) {
    val extra = davExtra()
    val meta = parseVideoMeta(title)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                "视频详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            if (player != null && error == null) {
                InlineVideoPlayer(player = player, onFullscreen = onFullscreen)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
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

            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 27.sp
            )
            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(16.dp))
            DetailCard {
                Text(
                    "文件信息",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DetailLine("类型", "视频")
                DetailLine("大小", if (size > 0) formatSize(size) else "未知")
                formatDavModified(lastModified)?.let { DetailLine("修改时间", it) }
                DetailLine("路径", prettyDavPath(href))
            }

            Spacer(Modifier.height(10.dp))
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Subtitles, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("外挂字幕", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (subtitleHref != null) "已自动匹配" else "未找到",
                        fontSize = 10.sp,
                        color = if (subtitleHref != null) MaterialTheme.colorScheme.primary
                        else extra.textMuted
                    )
                }
                Spacer(Modifier.height(7.dp))
                if (subtitleHref != null) {
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
                            append(" · 同名自动加载")
                        },
                        fontSize = 10.sp,
                        color = extra.textMuted
                    )
                } else {
                    Text(
                        "同目录没有与视频同名的 srt / ass / ssa / vtt 字幕。",
                        fontSize = 11.sp,
                        color = extra.textMuted
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "单击播放器控制栏显示播放进度，点右下角全屏按钮进入横屏播放。",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                color = extra.textMuted
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InlineVideoPlayer(
    player: ExoPlayer,
    onFullscreen: (Long) -> Unit
) {
    val ctx = LocalContext.current
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

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
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

        Box(
            Modifier
                .align(Alignment.Center)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable {
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
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
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMs(dragValue?.toLong() ?: position)} / ${formatMs(duration)}",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onFullscreen(player.currentPosition.coerceAtLeast(0)) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen, "横屏全屏",
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
