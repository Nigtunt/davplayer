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

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.keyboard)
        }
        .onChange(of: scenePhase) { phase in
            // 退到后台时让 Kotlin 侧重新上锁（隐私模式开启时才会锁）
            if phase == .background {
                PrivacyBridgeKt.onAppBackgrounded()
            }
        }
    }
}
