package com.davplayer.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        private const val EXTERNAL_SUBTITLE_LABEL = "davplayer-external:"
    }

    private val playerState = mutableStateOf<ExoPlayer?>(null)
    private var player: ExoPlayer?
        get() = playerState.value
        set(value) { playerState.value = value }
    private var davClient: WebDavClient? = null
    private var selectedSource: DavSource? = null
    private var videoUrl = ""
    private var videoHref = ""
    private var videoTitle = ""
    private var lastCoverCaptureAt = 0L
    private var switchingPlayer = false
    private var currentSpeed = 1f
    private var pendingStartPlayer: ExoPlayer? = null
    private var pendingOldPlayer: ExoPlayer? = null
    private var pendingPlayWhenReady = false
    private var dummySurface: Surface? = null
    private var dummySurfaceTexture: SurfaceTexture? = null
    private val historyStore by lazy { HistoryStore(this) }
    private val fullscreen = mutableStateOf(false)
    private val switching = mutableStateOf(false)
    private val decodeMode = mutableStateOf(VideoDecodeMode.HW_PLUS)
    private val embeddedGroups = mutableStateOf<List<Tracks.Group>>(emptyList())
    private val subtitleHref = mutableStateOf<String?>(null)
    private val subtitleName = mutableStateOf<String?>(null)
    private val playbackError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        videoHref = href
        videoTitle = title
        val startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        subtitleHref.value = intent.getStringExtra(EXTRA_SUBTITLE_HREF)
        subtitleName.value = intent.getStringExtra(EXTRA_SUBTITLE_NAME)
        val subtitleSize = intent.getLongExtra(EXTRA_SUBTITLE_SIZE, 0L)
        val size = intent.getLongExtra(EXTRA_SIZE, 0L)
        val lastModified = intent.getLongExtra(EXTRA_LAST_MODIFIED, 0L)
        val settings = Settings(this)
        val source = settings.activeSource()
        val unsupported = isUnsupportedVideo(title.ifBlank { href })

        selectedSource = source
        davClient = source?.let { WebDavClient(it.url, it.user, it.pass) }
        videoUrl = davClient?.fileUrl(href).orEmpty()
        val initialMode = if (looksLike10Bit(title)) {
            VideoDecodeMode.SW
        } else {
            VideoDecodeMode.fromSetting(settings.decodeMode)
        }
        decodeMode.value = initialMode
        player = if (source == null || unsupported) {
            null
        } else {
            createPlayer(initialMode, startPosition, playWhenReady = true, speed = 1f)
        }
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                if (player?.isPlaying == true) saveHistory()
            }
        }

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
                    player = player,
                    longPressSpeed = settings.longPressSpeed,
                    error = when {
                        source == null -> "没有可用的 WebDAV 源"
                        unsupported -> unsupportedMessage(title.ifBlank { href })
                        player == null -> "播放器初始化失败"
                        else -> null
                    } ?: playbackError.value,
                    client = davClient,
                    embedded = embeddedGroups.value,
                    subtitlesEnabled = embeddedGroups.value.any { it.isSelected } || subtitleHref.value != null,
                    fullscreen = fullscreen.value,
                    switching = switching.value,
                    decodeMode = decodeMode.value,
                    currentSpeed = currentSpeed,
                    onSwitchDecodeMode = { switchDecodeMode(it) },
                    onSpeedChanged = { currentSpeed = it },
                    onSurfaceReady = { onPlayerSurfaceReady() },
                    onBack = {
                        if (fullscreen.value) setPlayerFullscreen(false) else finish()
                    },
                    onToggleFullscreen = { setPlayerFullscreen(!fullscreen.value) },
                    onCaptureCover = { view -> captureCover(view) },
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

    private fun createPlayer(
        mode: VideoDecodeMode,
        startPosition: Long,
        playWhenReady: Boolean,
        speed: Float,
        muted: Boolean = false
    ): ExoPlayer? {
        val source = selectedSource ?: return null
        val client = davClient ?: WebDavClient(source.url, source.user, source.pass).also {
            davClient = it
        }
        if (videoUrl.isBlank()) videoUrl = client.fileUrl(videoHref)
        return runCatching {
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
                    .setLabel(EXTERNAL_SUBTITLE_LABEL + subHref.substringAfterLast('/'))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            val item = MediaItem.Builder()
                .setUri(videoUrl)
                .apply { if (sub != null) setSubtitleConfigurations(listOf(sub)) }
                .build()
            val preferFfmpeg = mode == VideoDecodeMode.SW
            AppLog.log(
                "PLAYER",
                "init mode=${mode.label} storage=${mode.storage} " +
                    "preferFfmpeg=$preferFfmpeg title=$videoTitle"
            )
            val renderersFactory = FfmpegRenderersFactory(this, preferFfmpeg).apply {
                setEnableDecoderFallback(true)
                if (mode == VideoDecodeMode.SW) {
                    setMediaCodecSelector { mimeType, secure, tunneling ->
                        MediaCodecSelector.DEFAULT
                            .getDecoderInfos(mimeType, secure, tunneling)
                            .sortedByDescending { it.softwareOnly }
                    }
                }
            }
            ExoPlayer.Builder(this, renderersFactory)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 45_000, 1_500, 3_000)
                        .build()
                )
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .build()
                .apply {
                    val exoPlayer = this
                    addListener(object : Player.Listener {
                        override fun onTracksChanged(tracks: Tracks) {
                            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                            val external = if (subtitleHref.value != null) {
                                textGroups.firstOrNull {
                                    it.getTrackFormat(0).label?.startsWith(EXTERNAL_SUBTITLE_LABEL) == true
                                } ?: textGroups.lastOrNull()
                            } else {
                                textGroups.firstOrNull {
                                    it.getTrackFormat(0).label?.startsWith(EXTERNAL_SUBTITLE_LABEL) == true
                                }
                            }
                            embeddedGroups.value = textGroups.filterNot { group ->
                                group === external ||
                                    group.getTrackFormat(0).label?.startsWith(EXTERNAL_SUBTITLE_LABEL) == true
                            }
                            if (external != null && !external.isSelected) {
                                applyTextTrack(exoPlayer, external)
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                this@VideoDetailActivity.saveHistory()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (!switchingPlayer) playingError(error)
                        }
                    })
                    addAnalyticsListener(object : AnalyticsListener {
                        override fun onTracksChanged(
                            eventTime: AnalyticsListener.EventTime,
                            tracks: Tracks
                        ) {
                            logTrackInfo(tracks)
                        }

                        override fun onVideoDecoderInitialized(
                            eventTime: AnalyticsListener.EventTime,
                            decoderName: String,
                            initializedTimestampMs: Long,
                            initializationDurationMs: Long
                        ) {
                            AppLog.log(
                                "CODEC",
                                "video decoder=$decoderName init=${initializationDurationMs}ms"
                            )
                        }

                        override fun onVideoInputFormatChanged(
                            eventTime: AnalyticsListener.EventTime,
                            format: Format,
                            decoderReuseEvaluation: DecoderReuseEvaluation?
                        ) {
                            AppLog.log(
                                "VIDEO",
                                "codec=${format.codecs ?: "-"} ${format.width}x${format.height} " +
                                    "bitrate=${format.bitrate}"
                            )
                        }

                        override fun onVideoCodecError(
                            eventTime: AnalyticsListener.EventTime,
                            error: Exception
                        ) {
                            AppLog.log(
                                "CODEC",
                                "video codec error ${error.javaClass.simpleName}: ${error.message}"
                            )
                        }

                        override fun onPlayerError(
                            eventTime: AnalyticsListener.EventTime,
                            error: PlaybackException
                        ) {
                            AppLog.log(
                                "PLAYER",
                                "analytic error ${error.errorCodeName}: ${error.message}; " +
                                    "cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}\n" +
                                    error.stackTraceToString()
                            )
                        }
                    })
                    if (muted) attachDummySurface(exoPlayer)
                    setMediaItem(item)
                    prepare()
                    if (startPosition > 0) seekTo(startPosition)
                    if (speed != 1f) playbackParameters = PlaybackParameters(speed)
                    volume = if (muted) 0f else 1f
                    this.playWhenReady = playWhenReady
                    if (!muted) currentSpeed = speed
                }
        }.onFailure {
            AppLog.log("PLAYER", "create failed mode=${mode.label}: ${it.message}")
        }.getOrNull()
    }

    private fun attachDummySurface(player: ExoPlayer) {
        releaseDummySurface()
        runCatching {
            val texture = SurfaceTexture(0)
            texture.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)
            player.setVideoSurface(surface)
            dummySurface = surface
            dummySurfaceTexture = texture
            AppLog.log("PLAYER", "shadow dummy surface attached")
        }.onFailure {
            AppLog.log("PLAYER", "shadow dummy surface failed: ${it.message}")
        }
    }

    private fun releaseDummySurface() {
        dummySurface?.release()
        dummySurfaceTexture?.release()
        dummySurface = null
        dummySurfaceTexture = null
    }

    private fun switchDecodeMode(mode: VideoDecodeMode) {
        if (mode == decodeMode.value || switchingPlayer) return
        val old = player ?: return
        val oldMode = decodeMode.value

        // HW copy 和 HW+ 共用 MediaCodec，只替换输出 Surface，不重建播放器。
        // 音频 Renderer 和播放时钟都保持不动。
        if (oldMode.usesMediaCodec && mode.usesMediaCodec) {
            switching.value = true
            decodeMode.value = mode
            Settings(this).decodeMode = mode.storage
            AppLog.log("PLAYER", "surface switch mode=${mode.label}")
            Handler(Looper.getMainLooper()).postDelayed({
                switching.value = false
            }, 220L)
            Toast.makeText(this, "解码：${mode.label}", Toast.LENGTH_SHORT).show()
            return
        }

        val position = old.currentPosition.coerceAtLeast(0)
        val playing = old.isPlaying || old.playWhenReady
        val speed = old.playbackParameters.speed
        switchingPlayer = true
        switching.value = true
        playbackError.value = null
        saveHistory()
        AppLog.log("PLAYER", "prepare switch ${oldMode.label} -> ${mode.label} at ${position}ms")

        // 先创建并准备目标模式播放器，旧播放器继续播放，准备失败也不会先黑屏。
        val shadow = createPlayer(
            mode = mode,
            startPosition = position,
            playWhenReady = false,
            speed = speed,
            muted = true
        )
        if (shadow == null) {
            switchingPlayer = false
            switching.value = false
            Toast.makeText(this, "切换 ${mode.label} 失败，继续使用当前解码", Toast.LENGTH_SHORT).show()
            return
        }

        var finished = false
        var timeout: Runnable? = null

        fun failSwitch(reason: String) {
            if (finished) return
            finished = true
            timeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
            switchingPlayer = false
            switching.value = false
            releaseDummySurface()
            shadow.release()
            AppLog.log("PLAYER", "switch failed mode=${mode.label}: $reason")
            Toast.makeText(this, "切换失败，继续使用当前解码", Toast.LENGTH_SHORT).show()
        }

        fun finishSwitch() {
            if (finished) return
            finished = true
            timeout?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
            // 准备期间旧播放器可能已经前进，替换前再对齐一次。
            val livePosition = old.currentPosition.coerceAtLeast(0)
            shadow.seekTo(livePosition)
            shadow.volume = 1f
            // 关键：先不要启动 shadow。FFmpeg/MediaCodec 需要先拿到新的
            // PlayerView Surface，否则会在无 surface 时渲染并抛 NPE。
            // 等 Compose 把新 Surface 接上后再 start。
            old.pause()
            currentSpeed = speed
            decodeMode.value = mode
            player = shadow
            pendingStartPlayer = shadow
            pendingOldPlayer = old
            pendingPlayWhenReady = playing
            Settings(this).decodeMode = mode.storage
            AppLog.log("PLAYER", "switch applied mode=${mode.label} at ${livePosition}ms")
            // 正常情况下 AndroidView 会立刻回调 onSurfaceReady；这里只是兜底。
            Handler(Looper.getMainLooper()).postDelayed({
                startPendingPlayer()
            }, 700L)
            Toast.makeText(this, "解码：${mode.label}", Toast.LENGTH_SHORT).show()
        }

        shadow.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) finishSwitch()
            }

            override fun onPlayerError(error: PlaybackException) {
                failSwitch(error.errorCodeName)
            }
        })
        if (shadow.playbackState == Player.STATE_READY) {
            finishSwitch()
        }

        timeout = Runnable { failSwitch("准备超时") }
        Handler(Looper.getMainLooper()).postDelayed(timeout!!, 10_000L)
    }

    private fun onPlayerSurfaceReady() {
        startPendingPlayer()
    }

    private fun startPendingPlayer() {
        val target = pendingStartPlayer ?: return
        if (player !== target) {
            pendingStartPlayer = null
            pendingOldPlayer?.release()
            pendingOldPlayer = null
            switchingPlayer = false
            switching.value = false
            return
        }
        pendingStartPlayer = null
        releaseDummySurface()
        target.playWhenReady = pendingPlayWhenReady
        AppLog.log("PLAYER", "switch start mode=${decodeMode.value.label}")
        val old = pendingOldPlayer
        pendingOldPlayer = null
        Handler(Looper.getMainLooper()).postDelayed({
            old?.release()
            switchingPlayer = false
            switching.value = false
        }, 320L)
    }

    private fun playingError(error: PlaybackException) {
        AppLog.log(
            "PLAYER",
            "播放失败 ${error.errorCodeName}: ${error.message}; " +
                "cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}\n" +
                error.stackTraceToString()
        )
        val base = describePlaybackError(error)
        playbackError.value = if (looksLike10Bit(videoTitle)) {
            "$base\n\n检测到 10bit H.264（Hi10P）。内置播放器已尝试软件解码；若设备没有可用软件解码器，" +
                "请点右上角「外部播放」用 VLC 等播放器打开。"
        } else {
            base
        }
    }

    private fun captureCover(view: View) {
        if (videoHref.isBlank()) return
        val exo = player ?: return
        // 必须等解码器真正出画面，避免刚进页面/退出时截到黑帧
        if (exo.playbackState != Player.STATE_READY ||
            exo.videoSize.width <= 0 ||
            exo.currentPosition < 2000L
        ) return
        val now = android.os.SystemClock.elapsedRealtime()
        // 同一张封面每 10 秒覆盖一次，避免生成大量文件
        if (lastCoverCaptureAt > 0 && now - lastCoverCaptureAt < 10_000L) return
        when (view) {
            is TextureView -> {
                val bitmap = view.bitmap ?: return
                lastCoverCaptureAt = now
                saveCoverBitmap(bitmap, recycleAfter = false)
            }
            is SurfaceView -> {
                if (view.width <= 0 || view.height <= 0) return
                lastCoverCaptureAt = now
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(
                    view,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            saveCoverBitmap(bitmap, recycleAfter = true)
                        } else {
                            bitmap.recycle()
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }
        }
    }

    private fun saveCoverBitmap(bitmap: Bitmap, recycleAfter: Boolean) {
        if (isMostlyBlack(bitmap)) {
            if (recycleAfter) bitmap.recycle()
            return
        }
        runCatching {
            historyStore.coverFile(videoHref).outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            }
        }
        if (recycleAfter) bitmap.recycle()
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val sample = runCatching {
            Bitmap.createScaledBitmap(bitmap, 12, 12, false)
        }.getOrNull() ?: return false
        val pixels = IntArray(12 * 12)
        sample.getPixels(pixels, 0, 12, 0, 0, 12, 12)
        if (sample !== bitmap) sample.recycle()
        var total = 0L
        pixels.forEach { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            total += (r * 299 + g * 587 + b * 114) / 1000
        }
        return total / pixels.size < 5
    }

    private fun loadSubtitle(entry: DavEntry) {
        val exo = player ?: return
        val client = davClient ?: return
        playbackError.value = null
        val wasPlaying = exo.isPlaying
        val position = exo.currentPosition.coerceAtLeast(0)
        val sub = MediaItem.SubtitleConfiguration.Builder(
            android.net.Uri.parse(client.fileUrl(entry.href))
        )
            .setMimeType(subtitleMime(entry.name))
            .setLanguage("und")
            .setLabel(EXTERNAL_SUBTITLE_LABEL + entry.name)
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

    private fun saveHistory() {
        val exo = player ?: return
        if (videoHref.isBlank()) return
        val previous = historyStore.get(videoHref)
        val duration = exo.duration.coerceAtLeast(previous?.durationMs ?: 0L).coerceAtLeast(1L)
        var position = exo.currentPosition.coerceAtLeast(0)
        if (duration > 1 && duration - position < 8000) position = 0
        historyStore.upsert(
            HistoryItem(
                href = videoHref,
                title = videoTitle,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun onPause() {
        saveHistory()
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        saveHistory()
        releaseDummySurface()
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
    longPressSpeed: Float,
    error: String?,
    client: WebDavClient?,
    embedded: List<Tracks.Group>,
    subtitlesEnabled: Boolean,
    fullscreen: Boolean,
    switching: Boolean,
    decodeMode: VideoDecodeMode,
    currentSpeed: Float,
    onSwitchDecodeMode: (VideoDecodeMode) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSurfaceReady: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onCaptureCover: (View) -> Unit,
    onPickSubtitle: (DavEntry) -> Unit,
    onSelectEmbedded: (Tracks.Group) -> Unit,
    onDisableSubtitles: () -> Unit
) {
    var showSubtitlePicker by remember { mutableStateOf(false) }
    val meta = parseVideoMeta(title)
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copyLink: () -> Unit = {
        client?.let {
            clipboard.setText(AnnotatedString(it.fileUrl(href)))
            Toast.makeText(ctx, "已复制直链", Toast.LENGTH_SHORT).show()
        }
        Unit
    }
    val externalPlay: () -> Unit = {
        client?.let {
            player?.pause()
            if (!openWithExternalPlayer(ctx, it.fileUrlWithCredentials(href), title)) {
                Toast.makeText(ctx, "没有找到可用的外部播放器", Toast.LENGTH_SHORT).show()
            }
        }
        Unit
    }

    if (fullscreen) {
        BackHandler { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (player != null && error == null) {
                InlineVideoPlayer(
                    player = player,
                    title = title,
                    quality = meta.labels.firstOrNull(),
                    longPressSpeed = longPressSpeed,
                    fullscreen = true,
                    switching = switching,
                    decodeMode = decodeMode,
                    initialSpeed = currentSpeed,
                    onSwitchDecodeMode = onSwitchDecodeMode,
                    onSpeedChanged = onSpeedChanged,
                    onSurfaceReady = onSurfaceReady,
                    onBack = onBack,
                    onToggleFullscreen = onToggleFullscreen,
                    onCopyLink = copyLink,
                    onExternalPlay = externalPlay,
                    onCaptureCover = onCaptureCover,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "无法播放", color = Color.White)
                    if (client != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = externalPlay) {
                            Text("用外部播放器打开", color = Color.White)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            if (player != null && error == null) {
                InlineVideoPlayer(
                    player = player,
                    title = title,
                    quality = meta.labels.firstOrNull(),
                    longPressSpeed = longPressSpeed,
                    fullscreen = false,
                    switching = switching,
                    decodeMode = decodeMode,
                    initialSpeed = currentSpeed,
                    onSwitchDecodeMode = onSwitchDecodeMode,
                    onSpeedChanged = onSpeedChanged,
                    onSurfaceReady = onSurfaceReady,
                    onBack = onBack,
                    onToggleFullscreen = onToggleFullscreen,
                    onCopyLink = copyLink,
                    onExternalPlay = externalPlay,
                    onCaptureCover = onCaptureCover,
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
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            error ?: "无法播放",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        if (client != null) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = externalPlay) {
                                Text("用外部播放器打开", color = Color.White)
                            }
                        }
                    }
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
    longPressSpeed: Float,
    fullscreen: Boolean,
    switching: Boolean,
    decodeMode: VideoDecodeMode,
    initialSpeed: Float,
    onSwitchDecodeMode: (VideoDecodeMode) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onSurfaceReady: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onCopyLink: () -> Unit,
    onExternalPlay: () -> Unit,
    onCaptureCover: (View) -> Unit,
    modifier: Modifier = Modifier
) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var videoSurface by remember { mutableStateOf<View?>(null) }
    var position by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var speed by remember(player) { mutableFloatStateOf(initialSpeed) }
    var boosting by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var decodeMenuOpen by remember { mutableStateOf(false) }

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

    LaunchedEffect(videoSurface, position) {
        if (position > 2000) {
            videoSurface?.let { onCaptureCover(it) }
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(modifier.background(Color.Black)) {
        key(decodeMode) {
            AndroidView(
                factory = { ctx ->
                    val layout = if (decodeMode == VideoDecodeMode.HW_COPY) {
                        R.layout.view_player_texture
                    } else {
                        R.layout.view_player_surface
                    }
                    (LayoutInflater.from(ctx).inflate(layout, null) as PlayerView).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, _ -> false }
                        (getVideoSurfaceView() as? SurfaceView)?.apply {
                            isClickable = false
                            isFocusable = false
                            setZOrderOnTop(false)
                            setZOrderMediaOverlay(false)
                        }
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(true)
                            setApplyEmbeddedFontSizes(true)
                            setStyle(CaptionStyleCompat.DEFAULT)
                        }
                    }.also { onSurfaceReady() }
                },
                update = { view ->
                    videoSurface = view.getVideoSurfaceView() as? View
                    onSurfaceReady()
                },
                modifier = Modifier.fillMaxSize().zIndex(-1f)
            )
        }

        // 空白区域手势：单击显隐控制层，双击全屏，长按临时倍速，横滑调进度。
        // 必须盖在 SurfaceView 上方，否则切到 surface_view 后手势会被原生视图吃掉。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controlsVisible, playing) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { onToggleFullscreen() },
                        onLongPress = {
                            boosting = true
                            player.setPlaybackSpeed(longPressSpeed)
                        },
                        onPress = {
                            awaitRelease()
                            if (boosting) {
                                player.setPlaybackSpeed(speed)
                                boosting = false
                            }
                        }
                    )
                }
                .pointerInput(duration) {
                    var target = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            target = player.currentPosition.coerceAtLeast(0)
                            seekPreview = target
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val dur = player.duration
                            if (dur > 0) {
                                target = (target + dragAmount / size.width * 120_000).toLong()
                                    .coerceIn(0, dur)
                                seekPreview = target
                            }
                        },
                        onDragEnd = {
                            seekPreview?.let { player.seekTo(it) }
                            seekPreview = null
                        },
                        onDragCancel = { seekPreview = null }
                    )
                }
        )

        if (boosting) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    "⚡ ${"%.1f".format(longPressSpeed)}x 播放中",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        seekPreview?.let { target ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "${formatMs(target)} / ${formatMs(duration)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        if (switching) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "切换解码中…",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
        Row(
            Modifier
                .fillMaxWidth()
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "更多", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("复制直链") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            menuOpen = false
                            onCopyLink()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("外部播放") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                        onClick = {
                            menuOpen = false
                            onExternalPlay()
                        }
                    )
                }
            }
        }
        }

        if (controlsVisible) {
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
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            val dur = duration.coerceAtLeast(1L)
            val fraction = (seekFraction ?: (position.toFloat() / dur.toFloat())).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(duration) {
                        detectTapGestures { tap ->
                            val f = (tap.x / size.width).coerceIn(0f, 1f)
                            player.seekTo((f * dur).toLong())
                        }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { seekFraction = fraction },
                            onHorizontalDrag = { change, _ ->
                                seekFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                change.consume()
                            },
                            onDragEnd = {
                                seekFraction?.let { player.seekTo((it * dur).toLong()) }
                                seekFraction = null
                            },
                            onDragCancel = { seekFraction = null }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.24f))
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(DavTheme.Coral)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction.coerceAtLeast(0.015f))
                        .height(16.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(DavTheme.Coral)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMs(seekFraction?.let { (it * dur).toLong() } ?: position)} / ${formatMs(duration)}",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { decodeMenuOpen = true }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            decodeMode.label,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DropdownMenu(
                        expanded = decodeMenuOpen,
                        onDismissRequest = { decodeMenuOpen = false }
                    ) {
                        VideoDecodeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (mode == decodeMode) "✓ ${mode.label}" else mode.label)
                                },
                                onClick = {
                                    decodeMenuOpen = false
                                    onSwitchDecodeMode(mode)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val presets = listOf(1f, 1.25f, 1.5f, 2f)
                            val next = presets[(presets.indexOf(speed).coerceAtLeast(0) + 1) % presets.size]
                            speed = next
                            player.setPlaybackSpeed(next)
                            onSpeedChanged(next)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (speed == 1f) "1.0x" else "${speed}x",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun looksLike10Bit(name: String): Boolean {
    val lower = name.lowercase()
    return "10bit" in lower || "10-bit" in lower || "10 bit" in lower || "hi10p" in lower
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
