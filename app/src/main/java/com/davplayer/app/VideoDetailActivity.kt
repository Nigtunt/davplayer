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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLDecoder

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val href = intent.getStringExtra(EXTRA_HREF) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: href.substringAfterLast('/')
        val position = intent.getLongExtra(EXTRA_POSITION, 0L)
        val subtitleHref = intent.getStringExtra(EXTRA_SUBTITLE_HREF)
        val subtitleName = intent.getStringExtra(EXTRA_SUBTITLE_NAME)
        val subtitleSize = intent.getLongExtra(EXTRA_SUBTITLE_SIZE, 0L)
        val size = intent.getLongExtra(EXTRA_SIZE, 0L)
        val lastModified = intent.getLongExtra(EXTRA_LAST_MODIFIED, 0L)
        val settings = Settings(this)

        setContent {
            DavThemeProvider(dark = resolveDark(settings.themeMode)) {
                VideoDetailScreen(
                    title = title,
                    href = href,
                    positionMs = position,
                    size = size,
                    lastModified = lastModified,
                    subtitleHref = subtitleHref,
                    subtitleName = subtitleName,
                    subtitleSize = subtitleSize,
                    onBack = { finish() },
                    onPlay = {
                        startPlayer(this, href, title, position, subtitleHref)
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoDetailScreen(
    title: String,
    href: String,
    positionMs: Long,
    size: Long,
    lastModified: Long,
    subtitleHref: String?,
    subtitleName: String?,
    subtitleSize: Long,
    onBack: () -> Unit,
    onPlay: () -> Unit
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF111827), Color(0xFF25314C))
                        )
                    )
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                meta.labels.firstOrNull()?.let { label ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow, "播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    if (positionMs > 0) "继续播放 · ${formatMs(positionMs)}" else "点击播放",
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
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

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (positionMs > 0) "继续播放" else "开始播放", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "播放页单击画面显示控制栏，双击画面切换横屏 / 竖屏全屏。",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                color = davExtra().textMuted
            )
            Spacer(Modifier.height(24.dp))
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
