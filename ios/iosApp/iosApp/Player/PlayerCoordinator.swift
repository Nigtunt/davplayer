import Foundation
import Combine
import UIKit
import SwiftUI
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

/// 播放器呈现协调器：直接把 SwiftUI 播放页以全窗口 overlay 加到 key window。
/// 不走 fullScreenCover / present，避免 Compose 承载层下 modal 不弹的问题。
final class PlayerCoordinator: ObservableObject {
    static let shared = PlayerCoordinator()

    @Published var request: PlayRequest?
    private var overlay: UIHostingController<VLCPlayerScreen>?

    private init() {}

    func present(_ request: PlayRequest) {
        DispatchQueue.main.async {
            guard let window = Self.keyWindow() else {
                PlayerBridge.shared.log(message: "播放器：找不到 key window")
                return
            }

            self.overlay?.view.removeFromSuperview()

            let host = UIHostingController(
                rootView: VLCPlayerScreen(
                    request: request,
                    onClose: { [weak self] in self?.dismiss() }
                )
            )
            host.view.backgroundColor = .black
            host.view.frame = window.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            window.addSubview(host.view)
            window.bringSubviewToFront(host.view)

            self.overlay = host
            self.request = request
            PlayerBridge.shared.markPlayerPresented()
            PlayerBridge.shared.log(message: "播放器：窗口 overlay 已加载 \(request.title)")
        }
    }

    func dismiss() {
        DispatchQueue.main.async {
            self.overlay?.view.removeFromSuperview()
            self.overlay = nil
            self.request = nil
        }
    }

    private static func keyWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let windows = scenes.flatMap { $0.windows }
        return windows.first(where: { $0.isKeyWindow }) ?? windows.first
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
