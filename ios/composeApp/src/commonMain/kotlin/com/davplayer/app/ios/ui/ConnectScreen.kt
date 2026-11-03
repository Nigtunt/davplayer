package com.davplayer.app.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.davplayer.app.ios.data.DavClient
import com.davplayer.app.ios.theme.Brand
import com.davplayer.app.ios.theme.Muted

/** 连接页：iPad 上居中成一张卡片，不会铺满整个宽屏 */
@Composable
fun ConnectScreen(onConnect: (DavClient) -> Unit) {
    var url by remember { mutableStateOf("http://192.168.1.10:5244/dav") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text("DavPlayer", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
            Spacer(Modifier.height(6.dp))
            Text("iOS 技术验证 · WebDAV", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("WebDAV 地址") }, singleLine = true,
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
                onClick = { onConnect(DavClient(url.trim(), user.trim(), pass)) },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("连接") }
        }
    }
}
