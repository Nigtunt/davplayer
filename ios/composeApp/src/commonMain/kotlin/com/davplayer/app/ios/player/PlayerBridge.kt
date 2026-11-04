package com.davplayer.app.ios.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.davplayer.app.ios.data.AppLog
import com.davplayer.app.ios.data.SourceStore

/**
 * 播放器桥。
 *
 * Kotlin 侧只把地址与认证信息交给 Swift，真正的解码播放由 Swift + MobileVLCKit 完成。
 * 这样不需要给 Kotlin/Native 做 VLCKit 的 cinterop（大框架绑定维护成本高、编译慢）。
 *
 * Swift 在 App 启动时把实现注册进来：`PlayerBridge.shared.presenter = SwiftPlayerPresenter()`。
 */
interface PlayerPresenter {
    fun present(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Long,
        sourceId: String,
        path: String
    )

    fun dismiss()
}

/** 窗口坐标（point），由 Compose 的 boundsInWindow 换算后传给 Swift */
data class PlayerFrame(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

/**
 * 内嵌播放器：Swift 在 key window 上叠加一个与 Compose 占位区域同尺寸的播放器视图。
 * 全屏由 Swift 自己扩展同一实例，Kotlin 只负责位置和生命周期。
 */
interface InlinePresenter {
    fun show(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Long,
        sourceId: String,
        path: String,
        frame: PlayerFrame
    )

    fun updateFrame(frame: PlayerFrame)
    fun hide()
    fun setSubtitle(url: String)
}

object PlayerBridge {
    private data class Session(val sourceId: String, val path: String, val title: String)

    private val sessions = mutableMapOf<String, Session>()

    /** 未注册（例如预览环境）时静默忽略 */
    var presenter: PlayerPresenter? = null
    var inlinePresenter: InlinePresenter? = null

    /** Swift 把播放器视图真正加到窗口后置 true；Kotlin 用它做 3 秒超时判断 */
    var playerPresented: Boolean by mutableStateOf(false)
        private set

    fun resetPlayerPresented() {
        playerPresented = false
    }

    fun markPlayerPresented() {
        playerPresented = true
        AppLog.info("Player", "播放器已呈现在窗口")
    }

    fun registerSession(url: String, sourceId: String, path: String, title: String) {
        if (sourceId.isNotBlank() && path.isNotBlank()) {
            sessions[url] = Session(sourceId, path, title)
        }
    }

    /** Swift 侧写运行日志，方便在 App 内排查播放器问题 */
    fun log(message: String) {
        AppLog.info("iOS", message)
    }

    /** Swift 每 5 秒、停止时回调；封面由 VLC 截图后传本地路径 */
    fun reportProgress(url: String, positionMs: Long, durationMs: Long, coverPath: String?) {
        val session = sessions[url] ?: return
        SourceStore.record(
            sourceId = session.sourceId,
            path = session.path,
            name = session.title,
            positionMs = positionMs,
            durationMs = durationMs,
            coverPath = coverPath
        )
    }

    fun present(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Long,
        sourceId: String,
        path: String
    ) {
        registerSession(url, sourceId, path, title)
        resetPlayerPresented()
        AppLog.info("Player", "全屏播放：$title @ ${startMs / 1000}s")
        presenter?.present(url, title, user, pass, startMs, sourceId, path)
    }

    fun dismiss() {
        presenter?.dismiss()
    }

    fun hideInline() {
        AppLog.info("Player", "关闭内嵌播放")
        inlinePresenter?.hide()
    }

    fun setSubtitle(url: String) {
        AppLog.info("Player", "加载外挂字幕")
        inlinePresenter?.setSubtitle(url)
    }
}
