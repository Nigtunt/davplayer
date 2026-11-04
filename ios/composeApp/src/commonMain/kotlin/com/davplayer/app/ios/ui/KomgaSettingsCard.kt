package com.davplayer.app.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.ComicStore
import com.davplayer.app.ios.data.KomgaClient
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Danger
import com.davplayer.app.ios.theme.Muted
import com.davplayer.app.ios.theme.SurfaceBg
import kotlinx.coroutines.launch

/** 「我的」里的 Komga 服务器配置 */
@Composable
fun KomgaSettingsCard() {
    val config = ComicStore.config
    var url by remember(config) {
        mutableStateOf(config.url.ifBlank { "http://192.168.1.10:25600" })
    }
    var email by remember(config) { mutableStateOf(config.email) }
    var pass by remember(config) { mutableStateOf(config.pass) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceBg, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Komga 服务器", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            if (config.isConfigured) {
                Text("已配置", fontSize = 10.sp, color = Brand)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text("漫画书架与阅读器；地址如 http://IP:25600", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = url, onValueChange = { url = it; saved = false },
            label = { Text("地址") }, singleLine = true,
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; saved = false },
            label = { Text("邮箱 / 用户名") }, singleLine = true,
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it; saved = false },
            label = { Text("密码") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, fontSize = 11.sp, color = Danger)
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("已保存，切到「漫画」即可使用", fontSize = 11.sp, color = Brand)
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    testing = true
                    error = null
                    saved = false
                    val ok = KomgaClient(url, email, pass).testConnection()
                    testing = false
                    if (ok) {
                        ComicStore.saveConfig(url, email, pass)
                        saved = true
                    } else {
                        error = "连接失败：请检查地址、邮箱和密码"
                    }
                }
            },
            enabled = url.isNotBlank() && !testing,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(if (testing) "测试中…" else "保存并测试", fontSize = 12.sp)
        }
    }
}
