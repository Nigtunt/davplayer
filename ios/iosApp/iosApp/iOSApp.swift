import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct DavPlayerIosApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var player = PlayerCoordinator.shared

    init() {
        // 把 Swift 的 VLCKit 播放器注册给 Kotlin 的 PlayerBridge
        PlayerBridge.shared.presenter = SwiftPlayerPresenter()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.keyboard)
                .fullScreenCover(item: $player.request) { request in
                    VLCPlayerScreen(request: request)
                }
        }
        .onChange(of: scenePhase) { phase in
            // 退到后台时让 Kotlin 侧重新上锁（隐私模式开启时才会锁）
            if phase == .background {
                PrivacyBridgeKt.onAppBackgrounded()
                // 播放器是全屏 modal，会盖住隐私锁的计算器，必须一起退出
                player.request = nil
            }
        }
    }
}
