import Foundation
import Combine
import UIKit
import ComposeApp

/// 一次播放请求：由 Kotlin 侧发起
struct PlayRequest: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
    let user: String
    let pass: String
    let startMs: Int64
}

/// 播放器呈现协调器：用 UIKit modal 直接 present，避免 SwiftUI fullScreenCover
/// 在 Compose UIViewController 承载下偶发不弹出的问题。
final class PlayerCoordinator: ObservableObject {
    static let shared = PlayerCoordinator()

    @Published var request: PlayRequest?
    private var presented: UIViewController?

    private init() {}

    func present(_ request: PlayRequest) {
        DispatchQueue.main.async {
            guard let root = Self.topViewController() else {
                PlayerBridge.shared.log(message: "播放器：找不到顶层 UIViewController")
                return
            }

            let host = UIHostingController(rootView: VLCPlayerScreen(request: request))
            host.modalPresentationStyle = .fullScreen
            host.view.backgroundColor = .black
            root.present(host, animated: true)

            self.presented = host
            self.request = request
            PlayerBridge.shared.log(message: "播放器：已 present \(request.title)")
        }
    }

    func dismiss() {
        DispatchQueue.main.async {
            self.presented?.dismiss(animated: true)
            self.presented = nil
            self.request = nil
        }
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let windows = scenes.flatMap { $0.windows }
        let window = windows.first(where: { $0.isKeyWindow }) ?? windows.first

        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// 实现 Kotlin 的 PlayerPresenter：Kotlin 只传地址和认证信息，播放交给 VLCKit
final class SwiftPlayerPresenter: NSObject, PlayerPresenter {
    func present(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Int64,
        sourceId: String,
        path: String
    ) {
        guard let parsed = URL(string: url) else {
            PlayerBridge.shared.log(message: "播放器：URL 无效 \(url)")
            return
        }
        PlayerCoordinator.shared.present(
            PlayRequest(
                url: parsed,
                title: title,
                user: user,
                pass: pass,
                startMs: startMs
            )
        )
    }

    func dismiss() {
        PlayerCoordinator.shared.dismiss()
    }
}
