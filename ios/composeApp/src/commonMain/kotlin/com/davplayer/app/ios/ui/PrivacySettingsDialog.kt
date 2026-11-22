package com.davplayer.app.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.PrivacyPin
import com.davplayer.app.ios.data.readPrivacyMode
import com.davplayer.app.ios.data.writePrivacyMode
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Muted

/** 设置弹窗：隐私模式开关 + 暗码设置（暗码输入直接在当前弹窗内完成） */
@Composable
fun PrivacySettingsDialog(onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(readPrivacyMode()) }
    var showPinSetup by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun openPinSetup() {
        pin = ""
        confirm = ""
        pinError = null
        showPinSetup = true
    }

    fun submitPin() {
        when {
            pin.length !in 6..12 || pin.startsWith("0") ->
                pinError = "请输入 6-12 位数字暗码，且不能以 0 开头"
            pin != confirm -> pinError = "两次输入的暗码不一致"
            PrivacyPin.setPin(pin) -> {
                writePrivacyMode(true)
                enabled = true
                showPinSetup = false
            }
            else -> pinError = "暗码保存失败"
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (showPinSetup) showPinSetup = false else onDismiss()
        },
        title = { Text(if (showPinSetup) "设置隐私暗码" else "设置") },
        text = {
            if (showPinSetup) {
                // 不再叠加第二个 AlertDialog，避免 iOS 键盘把嵌套弹窗推到屏幕顶部。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "首次开启隐私模式需要设置暗码。之后在计算器输入这串数字并按 = 进入应用。",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it.filter(Char::isDigit).take(12)
                            pinError = null
                        },
                        label = { Text("6-12 位数字暗码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = {
                            confirm = it.filter(Char::isDigit).take(12)
                            pinError = null
                        },
                        label = { Text("再次输入") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            } else {
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
                                    openPinSetup()
                                }
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { openPinSetup() }) {
                            Text(
                                if (PrivacyPin.hasPin()) "修改暗码" else "设置暗码",
                                fontSize = 12.sp,
                                color = Brand
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showPinSetup) {
                TextButton(onClick = { submitPin() }) { Text("保存") }
            } else {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (showPinSetup) {
                TextButton(onClick = { showPinSetup = false }) { Text("取消") }
            }
        }
    )
}
