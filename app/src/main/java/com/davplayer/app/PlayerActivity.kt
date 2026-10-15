package com.davplayer.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.FileOutputStream

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HREF = "href"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSITION = "pos"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        val startPos = intent.getLongExtra(EXTRA_POSITION, 0L)

        val settings = Settings(this)
        val store = HistoryStore(this)
        val client = WebDavClient(settings.serverUrl, settings.username, settings.password)
        val url = client.fileUrl(href)
        val headers = client.authHeaders()

        val dataSourceFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setDefaultRequestProperties(headers)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exo
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        if (startPos > 0) exo.seekTo(startPos)
        exo.playWhenReady = true

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlayerScreen(
                    player = exo,
                    title = title,
                    settings = settings,
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
                        // 仅在真正开始播放后抽帧做封面；未播放不触发任何缓存
                        val f = store.coverFile(href)
                        if (!f.exists()) {
                            runCatching {
                                val r = MediaMetadataRetriever()
                                if (headers.isEmpty()) r.setDataSource(url)
                                else r.setDataSource(url, headers)
                                var bmp = r.getFrameAtTime(
                                    exo.currentPosition * 1000,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                                if (bmp == null) bmp = r.getFrameAtTime(0)
                                r.release()
                                if (bmp != null) {
                                    FileOutputStream(f).use {
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    settings: Settings,
    onBack: () -> Unit,
    saveHistory: () -> Unit,
    ensureCover: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()

    val isPlaying = remember { mutableStateOf(false) }
    val isBuffering = remember { mutableStateOf(true) }
    val positionMs = remember { mutableStateOf(0L) }
    val durationMs = remember { mutableStateOf(0L) }
    val controlsVisible = remember { mutableStateOf(true) }
    val boosting = remember { mutableStateOf(false) }
    val baseSpeed = remember { mutableStateOf(1f) }
    val speedPresets = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f) }

    var coverTried by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                if (playing && !coverTried) {
                    coverTried = true
                    scope.launch {
                        delay(2000) // 播一小段再截帧，封面更有代表性
                        withContext(Dispatchers.IO) { ensureCover() }
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs.value = player.duration.coerceAtLeast(0)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            saveHistory()
        }
    }

    // 进度轮询 + 周期性保存历史
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

    // 控制栏自动隐藏
    LaunchedEffect(controlsVisible.value, isPlaying.value) {
        if (controlsVisible.value && isPlaying.value) {
            delay(4000)
            controlsVisible.value = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 手势层：点击切换控制栏，长按临时倍速
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            boosting.value = true
                            player.setPlaybackSpeed(settings.longPressSpeed)
                            waitForUpOrCancellation()
                            player.setPlaybackSpeed(baseSpeed.value)
                            boosting.value = false
                        } else {
                            controlsVisible.value = !controlsVisible.value
                        }
                    }
                }
        )

        // 缓冲指示
        if (isBuffering.value) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // 长按倍速提示
        if (boosting.value) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "${"%.1f".format(settings.longPressSpeed)}x 播放中",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White
                )
            }
        }

        // 控制栏
        AnimatedVisibility(
            visible = controlsVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableStateOf(0f) }
                val dur = durationMs.value.coerceAtLeast(0)
                val sliderValue = if (dragging) dragValue
                else positionMs.value.toFloat().coerceIn(0f, dur.toFloat().coerceAtLeast(1f))

                Slider(
                    value = sliderValue,
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = {
                        player.seekTo(dragValue.toLong())
                        dragging = false
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
                    TextButton(onClick = {
                        val cur = baseSpeed.value
                        val idx = speedPresets.indexOfFirst { it >= cur - 0.001f }
                        val next = speedPresets[(idx + 1) % speedPresets.size]
                        baseSpeed.value = next
                        if (!boosting.value) player.setPlaybackSpeed(next)
                    }) {
                        Text("${"%.2g".format(baseSpeed.value)}x", color = Color.White)
                    }
                }
            }
        }
    }
}
