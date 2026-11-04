package com.davplayer.app.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davplayer.app.ios.data.DavSource
import com.davplayer.app.ios.data.SourceStore
import com.davplayer.app.ios.theme.Bg
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Muted

/** 新增 / 编辑一个 WebDAV 源 */
@Composable
fun SourceEditorScreen(
    existing: DavSource?,
    canCancel: Boolean,
    onSave: (DavSource) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember(existing?.id) {
        mutableStateOf(existing?.url ?: "http://192.168.1.10:5244/dav")
    }
    var user by remember(existing?.id) { mutableStateOf(existing?.user.orEmpty()) }
    var pass by remember(existing?.id) { mutableStateOf(existing?.pass.orEmpty()) }

    Box(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canCancel) {
                    TextButton(onClick = onCancel) { Text("← 返回", fontSize = 12.sp, color = Muted) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (existing == null) "添加 WebDAV 源" else "编辑源",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("DavPlayer", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
            Spacer(Modifier.height(6.dp))
            Text("WebDAV 视频 · iPad", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("名称（可空，默认用主机名）") }, singleLine = true,
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("WebDAV 地址（含 /dav）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("用户名（可空）") }, singleLine = true,
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("密码（可空）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    onSave(
                        DavSource(
                            id = existing?.id ?: SourceStore.newId(),
                            name = name.trim().ifBlank { suggestName(cleanUrl) },
                            url = cleanUrl,
                            user = user.trim(),
                            pass = pass
                        )
                    )
                },
                enabled = url.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存并连接") }
            Spacer(Modifier.height(6.dp))
            Text(
                "提示：AList/OpenList 的地址是 http://IP:端口/dav",
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private fun suggestName(url: String): String {
    val host = url.substringAfter("://", url).substringBefore('/')
    return host.ifBlank { "WebDAV" }
}
