import SwiftUI
import UIKit
import ComposeApp

/// 让 App 先接住左右边缘滑动，避免 iPadOS 把边缘手势识别成切 App。
private final class EdgeDeferringHostController: UIViewController {
    private let child: UIViewController

    init(child: UIViewController) {
        self.child = child
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge {
        [.left, .right]
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(child)
        child.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(child.view)
        NSLayoutConstraint.activate([
            child.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            child.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            child.view.topAnchor.constraint(equalTo: view.topAnchor),
            child.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        child.didMove(toParent: self)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        EdgeDeferringHostController(child: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct DavPlayerIosApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // 把 Swift 的 VLCKit 播放器注册给 Kotlin 的 PlayerBridge
        PlayerBridge.shared.presenter = SwiftPlayerPresenter()
        // 横屏右侧的内嵌播放器
        PlayerBridge.shared.inlinePresenter = SwiftInlinePresenter()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                // 键盘弹出时 Compose 视图会收缩，露出的窗口底色默认是白的
                Color.black.ignoresSafeArea()
                ComposeView()
                    .ignoresSafeArea(.keyboard)
            }
        }
        .onChange(of: scenePhase) { phase in
            // 退到后台时让 Kotlin 侧重新上锁（隐私模式开启时才会锁）
            if phase == .background {
                PrivacyBridgeKt.onAppBackgrounded()
                // 播放器是 UIKit modal，会盖住隐私锁的计算器，必须一起退出
                PlayerCoordinator.shared.dismiss()
            }
        }
    }
}
