import SwiftUI
import UIKit
import ComposeApp

/// 全屏播放页：视频渲染交给 VLCKit，控制层用 SwiftUI
struct VLCPlayerScreen: View {
    let request: PlayRequest
    let onClose: () -> Void

    @StateObject private var controller = VLCPlayerController()
    @Environment(\.scenePhase) private var scenePhase

    @State private var showControls = true
    @State private var isScrubbing = false
    @State private var scrubTime: Double = 0
    @State private var hideWorkItem: DispatchWorkItem?
    @State private var longPressActive = false
    @State private var longPressBaseRate: Double = 1

    private let rates: [Double] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VideoSurface(view: controller.videoView)
                .ignoresSafeArea()

            if controller.isBuffering && controller.errorMessage == nil {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(.white)
                    .scaleEffect(1.4)
            }

            // 视频是 UIViewRepresentable（VLC 的 drawable），单独铺一层透明手势层
            // 保证任何位置点击都能呼出/收起控制栏
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
                // 不要把 tap 手势挂在整层控制栏上：点字幕 Menu 会先触发 toggleControls，
                // 控制栏被移除后 Menu 也随之消失。空白区域由后面的透明手势层处理。
                controlsOverlay
                    .transition(.opacity)
            }

            if let message = controller.errorMessage {
                errorOverlay(message)
            }
        }
        .statusBarHidden(true)
        .background(Color.black)
        .interactiveDismissDisabled(true)
        .onAppear {
            controller.onProgress = { position, duration, cover in
                PlayerBridge.shared.reportProgress(
                    url: request.url.absoluteString,
                    positionMs: Int64(position * 1000),
                    durationMs: Int64(duration * 1000),
                    coverPath: cover
                )
            }
            controller.start(
                url: request.url,
                user: request.user,
                pass: request.pass,
                startMs: request.startMs
            )
            scheduleHide()
        }
        .onDisappear {
            hideWorkItem?.cancel()
            controller.stop()
        }
        .onChange(of: scenePhase) { phase in
            // 退到后台直接退出播放器：隐私模式下不能让播放页盖住上锁的计算器
            if phase == .background {
                onClose()
            }
        }
        .onChange(of: controller.isPlaying) { playing in
            if playing {
                scheduleHide()
            }
        }
        .onChange(of: controller.isBuffering) { buffering in
            if !buffering {
                scheduleHide()
            }
        }
    }

    // MARK: - 控制层

    private var controlsOverlay: some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Button {
                    onClose()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .semibold))
                }

                Text(request.title)
                    .font(.system(size: 16, weight: .semibold))
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()

                if !request.subtitles.isEmpty || controller.subtitleIndexes.count > 1 {
                    subtitleMenu
                }
                rateMenu
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)

            Spacer()

            Button {
                controller.togglePlay()
                scheduleHide()
            } label: {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 42))
                    .frame(width: 82, height: 82)
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
                    Text(formatTime(isScrubbing ? scrubTime : controller.currentTime))
                    Spacer()
                    Text(formatTime(controller.duration))
                }
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(.white.opacity(0.75))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
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
            .ignoresSafeArea()
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
                .font(.system(size: 14, weight: .semibold))
                .frame(minWidth: 46, minHeight: 30)
                .background(Color.black.opacity(0.35))
                .clipShape(Capsule())
        }
    }

    private var subtitleMenu: some View {
        Menu {
            Button {
                controller.selectSubtitle(index: -1)
                scheduleHide()
            } label: {
                if controller.currentSubtitleIndex < 0 {
                    Label("关闭字幕", systemImage: "checkmark")
                } else {
                    Text("关闭字幕")
                }
            }

            if !controller.subtitleIndexes.isEmpty {
                Divider()
                ForEach(controller.subtitleIndexes.indices, id: \.self) { i in
                    let index = controller.subtitleIndexes[i]
                    let title = i < controller.subtitleTitles.count
                        ? controller.subtitleTitles[i]
                        : "字幕 \(i)"
                    Button {
                        controller.selectSubtitle(index: index)
                        scheduleHide()
                    } label: {
                        if index == controller.currentSubtitleIndex {
                            Label(title, systemImage: "checkmark")
                        } else {
                            Text(title)
                        }
                    }
                }
            }

            if !request.subtitles.isEmpty {
                Divider()
                Text("外挂字幕")
                ForEach(request.subtitles.indices, id: \.self) { i in
                    let sub = request.subtitles[i]
                    Button {
                        controller.loadSubtitle(sub.url)
                        scheduleHide()
                    } label: {
                        Text(sub.name)
                    }
                }
            }
        } label: {
            Image(systemName: "captions.bubble")
                .font(.system(size: 18, weight: .semibold))
                .frame(width: 46, height: 30)
                .background(Color.black.opacity(0.35))
                .clipShape(Capsule())
        }
    }

    private func errorOverlay(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 38))
            Text(message)
                .font(.system(size: 15))
                .multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button("重试") {
                    controller.retry()
                    scheduleHide()
                }
                .buttonStyle(.borderedProminent)

                Button("返回") {
                    onClose()
                }
                .buttonStyle(.bordered)
            }
            .padding(.top, 4)
        }
        .foregroundColor(.white)
        .padding(28)
        .background(Color.black.opacity(0.78))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .padding(40)
        .tint(.white)
    }

    // MARK: - 手势与自动隐藏

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
        // 不能在这里提前 return：onAppear 时 isPlaying 还是 false，
        // 会导致控制栏永远不隐藏。改为 4 秒后检查。
        let item = DispatchWorkItem {
            guard controller.isPlaying else { return }
            withAnimation(.easeInOut(duration: 0.25)) {
                showControls = false
            }
        }
        hideWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: item)
    }
}

/// 承载 VLCKit 的 drawable
private struct VideoSurface: UIViewRepresentable {
    let view: UIView

    func makeUIView(context: Context) -> UIView {
        view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

private func formatTime(_ seconds: Double) -> String {
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
