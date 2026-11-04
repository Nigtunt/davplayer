package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.AppLog
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Hairline
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg

/** 「我的」里的日志入口卡片 */
@Composable
fun LogCard(onView: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("运行日志", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "最近 ${AppLog.entries.size} 条 · 网络/接口/播放器排查",
                fontSize = 11.sp,
                color = Muted
            )
        }
        TextButton(onClick = onView) { Text("查看", fontSize = 12.sp, color = com.davplayer.app.ios.theme.Brand) }
    }
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← 返回", fontSize = 12.sp, color = Muted) }
            Text(
                "运行日志",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("UTC", fontSize = 10.sp, color = Muted)
            TextButton(onClick = { AppLog.clear() }) { Text("清空", fontSize = 12.sp, color = Muted) }
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)

        val logs = AppLog.entries
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无日志", fontSize = 13.sp, color = Muted)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(logs.reversed()) { entry ->
                    Text(
                        "${clock(entry.time)}  ${entry.level}/${entry.tag}  ${entry.message}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (entry.level) {
                            "E" -> Danger
                            "W" -> Color(0xFFE0A040)
                            else -> Muted
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000) % 86_400
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun pad(v: Long) = if (v < 10) "0$v" else v.toString()
    return "${pad(h)}:${pad(m)}:${pad(s)}"
}
