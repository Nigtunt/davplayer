import SwiftUI
import UIKit
import ComposeApp

/// 右侧内嵌播放器：由 Kotlin 传入窗口坐标，Swift 在 key window 上叠加一个同尺寸的播放视图。
/// 全屏时只改变这个视图的 frame，不重建 VLC 实例，播放不中断。
final class InlinePlayerHost: NSObject {
    static let shared = InlinePlayerHost()

    private var hosting: UIHostingController<InlinePlayerView>?
    private var controller: VLCPlayerController?
    private var inlineFrame: CGRect = .zero
    private var isFullscreen = false
    private var orientationObserver: NSObjectProtocol?

    private var keyWindow: UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }

    func show(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Int64,
        sourceId: String,
        path: String,
        frame: PlayerFrame
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            guard let parsed = URL(string: url), let window = self.keyWindow else {
                PlayerBridge.shared.log(message: "内嵌播放：找不到 key window 或 URL")
                return
            }
            self.removeNow()

            let controller = VLCPlayerController()
            let root = InlinePlayerView(
                controller: controller,
                title: title,
                onToggleFullscreen: { [weak self] full in self?.setFullscreen(full) }
            )
            let host = InlineHostingController(rootView: root)
            host.view.backgroundColor = .black

            let rect = CGRect(
                x: frame.x,
                y: frame.y,
                width: max(frame.width, 1),
                height: max(frame.height, 1)
            )
            host.view.frame = rect
            window.addSubview(host.view)
            window.bringSubviewToFront(host.view)

            self.hosting = host
            self.controller = controller
            self.inlineFrame = rect
            self.isFullscreen = false

            PlayerBridge.shared.log(message: "内嵌播放：\(title) frame=\(rect)")
            controller.onProgress = { position, duration, cover in
                PlayerBridge.shared.reportProgress(
                    url: url,
                    positionMs: Int64(position * 1000),
                    durationMs: Int64(duration * 1000),
                    coverPath: cover
                )
            }
            controller.start(
                url: parsed,
                user: user,
                pass: pass,
                startMs: startMs
            )

            UIDevice.current.beginGeneratingDeviceOrientationNotifications()
            self.orientationObserver = NotificationCenter.default.addObserver(
                forName: UIDevice.orientationDidChangeNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.handleOrientationChange()
            }
        }
    }

    func updateFrame(frame: PlayerFrame) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let rect = CGRect(
                x: frame.x,
                y: frame.y,
                width: max(frame.width, 1),
                height: max(frame.height, 1)
            )
            self.inlineFrame = rect
            if !self.isFullscreen {
                self.hosting?.view.frame = rect
            }
        }
    }

    func hide() {
        DispatchQueue.main.async { [weak self] in
            self?.removeNow()
        }
    }

    func setSubtitle(url: String) {
        DispatchQueue.main.async { [weak self] in
            self?.controller?.loadSubtitle(url)
        }
    }

    /// 已在主线程时直接清理，避免 show() 里排队的 hide 把新实例删掉
    private func removeNow() {
        if let observer = orientationObserver {
            NotificationCenter.default.removeObserver(observer)
            orientationObserver = nil
            UIDevice.current.endGeneratingDeviceOrientationNotifications()
        }
        controller?.stop()
        controller = nil
        hosting?.view.removeFromSuperview()
        hosting = nil
        isFullscreen = false
    }

    private func setFullscreen(_ full: Bool) {
        guard let host = hosting, let window = keyWindow else { return }
        isFullscreen = full
        window.bringSubviewToFront(host.view)
        let target = full ? window.bounds : inlineFrame
        UIView.animate(withDuration: 0.25) {
            host.view.frame = target
        }
    }

    private func handleOrientationChange() {
        guard isFullscreen, let host = hosting, let window = keyWindow else { return }
        host.view.frame = window.bounds
    }
}

/// 让全屏时隐藏状态栏/Home 指示条
private final class InlineHostingController<Content: View>: UIHostingController<Content> {
    override var prefersStatusBarHidden: Bool { true }
    override var prefersHomeIndicatorAutoHidden: Bool { true }
}

/// 实现 Kotlin 的 InlinePresenter
final class SwiftInlinePresenter: NSObject, InlinePresenter {
    func show(
        url: String,
        title: String,
        user: String,
        pass: String,
        startMs: Int64,
        sourceId: String,
        path: String,
        frame: PlayerFrame
    ) {
        InlinePlayerHost.shared.show(
            url: url,
            title: title,
            user: user,
            pass: pass,
            startMs: startMs,
            sourceId: sourceId,
            path: path,
            frame: frame
        )
    }

    func updateFrame(frame: PlayerFrame) {
        InlinePlayerHost.shared.updateFrame(frame: frame)
    }

    func hide() {
        InlinePlayerHost.shared.hide()
    }

    func setSubtitle(url: String) {
        InlinePlayerHost.shared.setSubtitle(url: url)
    }
}

// MARK: - SwiftUI 视图

private struct InlinePlayerView: View {
    @ObservedObject var controller: VLCPlayerController
    let title: String
    let onToggleFullscreen: (Bool) -> Void

    @State private var fullscreen = false
    @State private var showControls = true
    @State private var isScrubbing = false
    @State private var scrubTime: Double = 0
    @State private var hideWorkItem: DispatchWorkItem?
    @State private var longPressActive = false
    @State private var longPressBaseRate: Double = 1

    private let rates: [Double] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0]

    var body: some View {
        ZStack {
            Color.black

            InlineVideoSurface(view: controller.videoView)

            if controller.isBuffering && controller.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
                    .scaleEffect(1.2)
            }

            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { toggleControls() }
                .onLongPressGesture(
                    minimumDuration: 0.5,
                    maximumDistance: 20,
                    pressing: { pressing in
                        if pressing && !longPressActive {
                            longPressActive = true
                            longPressBaseRate = controller.rate
                            controller.setRate(2.0)
                        } else if !pressing && longPressActive {
                            longPressActive = false
                            controller.setRate(longPressBaseRate)
                        }
                    },
                    perform: {}
                )
                .simultaneousGesture(
                    DragGesture(minimumDistance: 20).onEnded { value in
                        let delta = Double(value.translation.width / 300.0) * 120.0
                        controller.seek(to: controller.currentTime + delta)
                    }
                )

            if showControls {
                controlsOverlay
                    .contentShape(Rectangle())
                    .onTapGesture { toggleControls() }
                    .transition(.opacity)
            }

            if let message = controller.errorMessage {
                errorOverlay(message)
            }
        }
        .background(Color.black)
        .onAppear { scheduleHide() }
        .onDisappear { hideWorkItem?.cancel() }
        .onChange(of: controller.isPlaying) { playing in
            if playing { scheduleHide() }
        }
    }

    private var controlsOverlay: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                if controller.subtitleIndexes.count > 1 {
                    subtitleMenu
                }
                rateMenu

                Button {
                    fullscreen.toggle()
                    onToggleFullscreen(fullscreen)
                    scheduleHide()
                } label: {
                    Image(systemName: fullscreen
                        ? "arrow.down.right.and.arrow.up.left"
                        : "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 34, height: 30)
                        .background(Color.black.opacity(0.35))
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)

            Spacer()

            Button {
                controller.togglePlay()
                scheduleHide()
            } label: {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 26))
                    .frame(width: 58, height: 58)
                    .background(Color.black.opacity(0.35))
                    .clipShape(Circle())
            }

            Spacer()

            VStack(spacing: 4) {
                Slider(
                    value: sliderBinding,
                    in: 0...max(controller.duration, 1),
                    onEditingChanged: { editing in
                        isScrubbing = editing
                        if editing {
                            hideWorkItem?.cancel()
                        } else {
                            controller.seek(to: scrubTime)
                            scheduleHide()
                        }
                    }
                )
                .tint(.white)

                HStack {
                    Text(formatClock(isScrubbing ? scrubTime : controller.currentTime))
                    Spacer()
                    Text(formatClock(controller.duration))
                }
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.white.opacity(0.75))
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
        .foregroundColor(.white)
        .background(
            LinearGradient(
                gradient: Gradient(colors: [
                    .black.opacity(0.75),
                    .black.opacity(0.0),
                    .black.opacity(0.0),
                    .black.opacity(0.85)
                ]),
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private var sliderBinding: Binding<Double> {
        Binding(
            get: { isScrubbing ? scrubTime : controller.currentTime },
            set: { value in
                isScrubbing = true
                scrubTime = value
            }
        )
    }

    private var rateMenu: some View {
        Menu {
            ForEach(rates, id: \.self) { value in
                Button {
                    controller.setRate(value)
                    scheduleHide()
                } label: {
                    if abs(controller.rate - value) < 0.01 {
                        Label(rateText(value), systemImage: "checkmark")
                    } else {
                        Text(rateText(value))
                    }
                }
            }
        } label: {
            Text(rateText(controller.rate))
                .font(.system(size: 12, weight: .semibold))
                .frame(minWidth: 40, minHeight: 30)
                .background(Color.black.opacity(0.35))
                .clipShape(Capsule())
        }
    }

    private var subtitleMenu: some View {
        Menu {
            ForEach(controller.subtitleIndexes.indices, id: \.self) { i in
                let index = controller.subtitleIndexes[i]
                let name = i < controller.subtitleTitles.count
                    ? controller.subtitleTitles[i]
                    : "字幕 \(i)"
                Button {
                    controller.selectSubtitle(index: index)
                    scheduleHide()
                } label: {
                    if index == controller.currentSubtitleIndex {
                        Label(name, systemImage: "checkmark")
                    } else {
                        Text(name)
                    }
                }
            }
        } label: {
            Image(systemName: "captions.bubble")
                .font(.system(size: 15, weight: .semibold))
                .frame(width: 38, height: 30)
                .background(Color.black.opacity(0.35))
                .clipShape(Capsule())
        }
    }

    private func errorOverlay(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 28))
            Text(message)
                .font(.system(size: 13))
                .multilineTextAlignment(.center)
            Button("重试") {
                controller.retry()
                scheduleHide()
            }
            .buttonStyle(.borderedProminent)
        }
        .foregroundColor(.white)
        .padding(20)
        .background(Color.black.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(24)
        .tint(.white)
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) {
            showControls.toggle()
        }
        if showControls {
            scheduleHide()
        } else {
            hideWorkItem?.cancel()
        }
    }

    private func scheduleHide() {
        hideWorkItem?.cancel()
        guard controller.isPlaying else { return }
        let item = DispatchWorkItem {
            withAnimation(.easeInOut(duration: 0.25)) {
                showControls = false
            }
        }
        hideWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: item)
    }
}

private struct InlineVideoSurface: UIViewRepresentable {
    let view: UIView

    func makeUIView(context: Context) -> UIView { view }
    func updateUIView(_ uiView: UIView, context: Context) {}
}

private func formatClock(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0 else { return "00:00" }
    let total = Int(seconds.rounded())
    let h = total / 3600
    let m = (total % 3600) / 60
    let s = total % 60
    return h > 0
        ? String(format: "%d:%02d:%02d", h, m, s)
        : String(format: "%02d:%02d", m, s)
}

private func rateText(_ value: Double) -> String {
    if abs(value - value.rounded()) < 0.001 {
        return "\(Int(value.rounded()))x"
    }
    return String(format: "%gx", value)
}
