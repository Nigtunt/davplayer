package com.davplayer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** GitHub Release 信息 */
data class ReleaseInfo(
    val version: String,   // 2.1.0（已去掉 v 前缀）
    val tag: String,       // v2.1.0
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val size: Long
)

/**
 * 应用自更新：检查 GitHub Releases → 下载 apk → 交给系统安装器。
 * 注意：能覆盖安装的前提是发布包与本机已安装的包签名一致。
 */
object Updater {

    /** 发布仓库，换仓库只改这里 */
    private const val REPO = "Nigtunt/davplayer"
    private val API = "https://api.github.com/repos/$REPO/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun currentVersion(ctx: Context): String = runCatching {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    /** 检查最新 Release；没有更新时返回 null */
    suspend fun checkLatest(ctx: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        val body = http.newCall(
            Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DavPlayer")
                .build()
        ).execute().use { resp ->
            if (resp.code == 404) throw IOException("仓库还没有发布 Release")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("响应为空")
        }

        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return@withContext null
        val version = tag.removePrefix("v").removePrefix("V")
        if (!isNewer(version, currentVersion(ctx))) return@withContext null

        var apkUrl = ""
        var apkName = ""
        var size = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    apkName = name
                    size = a.optLong("size")
                    break
                }
            }
        }
        if (apkUrl.isBlank()) return@withContext null

        ReleaseInfo(
            version = version,
            tag = tag,
            notes = json.optString("body").trim().take(4000),
            apkUrl = apkUrl,
            apkName = apkName,
            size = size
        )
    }

    /** latest 是否比 current 新 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(v: String): List<Int> = v.trim()
        .removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.')
        .map { it.trim().toIntOrNull() ?: 0 }

    /** 下载 apk 到应用私有目录（FileProvider 可读） */
    suspend fun download(ctx: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "apk").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val target = File(dir, info.apkName.ifBlank { "davplayer-${info.version}.apk" })

            val req = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "DavPlayer")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("响应为空")
                val total = body.contentLength().takeIf { it > 0 } ?: info.size
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (target.length() <= 0) throw IOException("下载内容为空")
            target
        }

    /** 下载到的 apk 与本机已安装包是否同签名（不同签名无法覆盖安装） */
    fun isSameSignature(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val incoming = pm.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
            ?.signatures?.firstOrNull()?.toByteArray()
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            .signatures?.firstOrNull()?.toByteArray()
        incoming != null && installed != null && incoming.contentEquals(installed)
    }.getOrDefault(false)

    /** Android 8+ 需要「安装未知应用」权限 */
    fun canInstallPackages(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 拉起系统安装器 */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }
}

/** 检查/下载/安装的界面状态，App 与设置页共用 */
class UpdateState {
    var checking by mutableStateOf(false)
    var info by mutableStateOf<ReleaseInfo?>(null)
    var downloading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    val latestVersion: String? get() = info?.tag

    suspend fun check(ctx: Context) {
        if (checking) return
        checking = true
        error = null
        message = null
        try {
            val latest = Updater.checkLatest(ctx)
            if (latest == null) {
                info = null
                message = "已是最新版本 v${Updater.currentVersion(ctx)}"
            } else {
                info = latest
                message = "发现新版本 ${latest.tag}"
            }
        } catch (e: Exception) {
            error = "检查失败：${e.message}"
            AppLog.log("UPDATE", "检查更新失败：${e.message}")
        }
        checking = false
    }

    /** 下载并拉起安装器 */
    suspend fun downloadAndInstall(ctx: Context) {
        val release = info ?: return
        if (downloading) return
        downloading = true
        progress = 0f
        error = null
        message = null
        try {
            val apk = Updater.download(ctx, release) { progress = it }
            AppLog.log("UPDATE", "已下载 ${release.apkName}（${apk.length() / 1024 / 1024}MB）")
            if (!Updater.isSameSignature(ctx, apk)) {
                message = "安装包签名与当前应用不一致，无法覆盖安装。请卸载后手动安装，或让发布包使用固定签名。"
                downloading = false
                return
            }
            if (!Updater.canInstallPackages(ctx)) {
                message = "需要先允许「安装未知应用」"
                Updater.openInstallPermission(ctx)
                downloading = false
                return
            }
            message = "下载完成，正在调起安装…"
            Updater.install(ctx, apk)
        } catch (e: Exception) {
            error = "下载失败：${e.message}"
            AppLog.log("UPDATE", "下载失败：${e.message}")
        }
        downloading = false
    }
}

/** 新版本提示框：显示更新说明 + 下载进度 + 立即更新 */
@Composable
fun UpdateDialog(state: UpdateState, currentVersion: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = state.info ?: return

    AlertDialog(
        onDismissRequest = { if (!state.downloading) onDismiss() },
        title = { Text("发现新版本 ${info.tag}") },
        text = {
            Column {
                Text(
                    "当前版本 v$currentVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    info.notes.ifBlank { "（该版本未填写更新说明）" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                )
                if (state.downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "正在下载 ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                state.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.downloading,
                onClick = { scope.launch { state.downloadAndInstall(ctx) } }
            ) { Text(if (state.downloading) "下载中…" else "立即更新") }
        },
        dismissButton = {
            TextButton(enabled = !state.downloading, onClick = onDismiss) { Text("稍后") }
        }
    )
}
