import Foundation
import Combine
import ComposeApp

/// 一次播放请求：由 Kotlin 侧发起
struct PlayRequest: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
    let user: String
    let pass: String
}

/// 播放器呈现协调器（SwiftUI fullScreenCover 绑定这个对象）
final class PlayerCoordinator: ObservableObject {
    static let shared = PlayerCoordinator()

    @Published var request: PlayRequest?

    private init() {}

    func present(_ request: PlayRequest) {
        // Kotlin 可能在 Compose 的点击回调里调用，异步回到主线程避免 SwiftUI 更新冲突
        DispatchQueue.main.async {
            self.request = request
        }
    }

    func dismiss() {
        DispatchQueue.main.async {
            self.request = nil
        }
    }
}

/// 实现 Kotlin 的 PlayerPresenter：Kotlin 只传地址和认证信息，播放交给 VLCKit
final class SwiftPlayerPresenter: NSObject, PlayerPresenter {
    func present(url: String, title: String, user: String, pass: String) {
        guard let parsed = URL(string: url) else { return }
        PlayerCoordinator.shared.present(
            PlayRequest(url: parsed, title: title, user: user, pass: pass)
        )
    }

    func dismiss() {
        PlayerCoordinator.shared.dismiss()
    }
}
