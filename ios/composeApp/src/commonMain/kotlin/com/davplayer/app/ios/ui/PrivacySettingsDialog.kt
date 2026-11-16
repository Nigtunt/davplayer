package com.davplayer.app.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.davplayer.app.ios.data.PrivacyPin
import com.davplayer.app.ios.data.readPrivacyMode
import com.davplayer.app.ios.data.writePrivacyMode
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Muted

/** 设置弹窗：隐私模式开关 + 暗码设置 */
@Composable
fun PrivacySettingsDialog(onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(readPrivacyMode()) }
    var showPinDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("隐私模式", fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "启动时显示计算器，输入隐私暗码后按 = 进入应用；退到后台会重新上锁",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                enabled = false
                                writePrivacyMode(false)
                            } else if (PrivacyPin.hasPin()) {
                                enabled = true
                                writePrivacyMode(true)
                            } else {
                                // 首次开启必须先配置暗码，不再使用内置默认值
                                showPinDialog = true
                            }
                        }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showPinDialog = true }) {
                        Text(
                            if (PrivacyPin.hasPin()) "修改暗码" else "设置暗码",
                            fontSize = 12.sp,
                            color = Brand
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )

    if (showPinDialog) {
        PrivacyPinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                if (PrivacyPin.setPin(pin)) {
                    writePrivacyMode(true)
                    enabled = true
                    showPinDialog = false
                }
            }
        )
    }
}
