package com.davplayer.app.ios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.PRIVACY_CODE
import com.davplayer.app.ios.data.readPrivacyMode
import com.davplayer.app.ios.data.writePrivacyMode
import com.davplayer.app.ios.theme.Muted

/** 设置弹窗：目前只有隐私模式开关 */
@Composable
fun PrivacySettingsDialog(onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(readPrivacyMode()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("隐私模式", fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "启动时显示计算器，输入 $PRIVACY_CODE 后按 = 进入应用",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        writePrivacyMode(it)
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
