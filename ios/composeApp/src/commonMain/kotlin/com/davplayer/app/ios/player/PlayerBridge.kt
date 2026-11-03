package com.davplayer.app.ios.player

/**
 * 播放器桥。
 *
 * Kotlin 侧只把地址与认证信息交给 Swift，真正的解码播放由 Swift + MobileVLCKit 完成。
 * 这样不需要给 Kotlin/Native 做 VLCKit 的 cinterop（大框架绑定维护成本高、编译慢）。
 *
 * Swift 在 App 启动时把实现注册进来：`PlayerBridge.shared.presenter = SwiftPlayerPresenter()`。
 */
interface PlayerPresenter {
    fun present(url: String, title: String, user: String, pass: String)
    fun dismiss()
}

object PlayerBridge {
    /** 未注册（例如预览环境）时静默忽略 */
    var presenter: PlayerPresenter? = null

    fun present(url: String, title: String, user: String, pass: String) {
        presenter?.present(url, title, user, pass)
    }

    fun dismiss() {
        presenter?.dismiss()
    }
}
