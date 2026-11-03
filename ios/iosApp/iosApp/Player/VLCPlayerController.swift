import AVFoundation
import Combine
import Foundation
import MobileVLCKit
import UIKit

/// 封装 VLCMediaPlayer，只暴露 SwiftUI 需要的状态与操作。
/// 注意：VLCKit 的调用尽量都放在主线程，delegate 回调统一回主线程。
final class VLCPlayerController: NSObject, ObservableObject, VLCMediaPlayerDelegate {

    let player = VLCMediaPlayer()
    /// 传给 VLCKit 的渲染视图（SwiftUI 用 UIViewRepresentable 承载）
    let videoView = UIView()

    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = true
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var rate: Double = 1
    @Published private(set) var errorMessage: String?
    @Published private(set) var subtitleTitles: [String] = []
    @Published private(set) var subtitleIndexes: [Int32] = []
    @Published private(set) var currentSubtitleIndex: Int32 = -1

    private var mediaURL: URL?
    private var httpUser = ""
    private var httpPass = ""
    private var desiredRate: Float = 1

    override init() {
        super.init()
        videoView.backgroundColor = .black
        videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        player.delegate = self
        player.drawable = videoView
    }

    // MARK: - 生命周期

    func start(url: URL, user: String, pass: String) {
        mediaURL = url
        httpUser = user
        httpPass = pass
        activateAudioSession()
        playCurrent()
    }

    func retry() {
        playCurrent()
    }

    func stop() {
        player.stop()
        player.drawable = nil
        UIApplication.shared.isIdleTimerDisabled = false
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    // MARK: - 播放控制

    func togglePlay() {
        if player.isPlaying {
            player.pause()
        } else {
            player.play()
            if desiredRate != 1 {
                player.rate = desiredRate
            }
        }
    }

    func seek(to seconds: Double) {
        guard duration > 0 else { return }
        let target = min(max(seconds, 0), duration)
        player.time = VLCTime(int: Int32(target * 1000))
        currentTime = target
    }

    func setRate(_ value: Double) {
        rate = value
        desiredRate = Float(value)
        player.rate = Float(value)
    }

    func selectSubtitle(index: Int32) {
        player.currentVideoSubTitleIndex = index
        currentSubtitleIndex = index
    }

    // MARK: - 私有

    private func playCurrent() {
        guard let url = mediaURL else { return }

        errorMessage = nil
        isBuffering = true
        isPlaying = false
        currentTime = 0
        duration = 0
        subtitleTitles = []
        subtitleIndexes = []
        currentSubtitleIndex = -1

        let media = VLCMedia(url: url)
        // WebDAV 走网络，给一点缓冲；Basic 认证用 media option，避免密码出现在 URL 里
        var options: [AnyHashable: Any] = ["network-caching": 2000]
        if !httpUser.isEmpty {
            options["http-user"] = httpUser
            options["http-pwd"] = httpPass
        }
        media.addOptions(options)

        player.media = media
        player.play()
    }

    private func activateAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback, options: [])
        try? session.setActive(true)
    }

    private func refreshTracks() {
        let names = player.videoSubTitlesNames as? [String] ?? []
        let indexes = (player.videoSubTitlesIndexes as? [NSNumber] ?? []).map { $0.int32Value }
        subtitleTitles = names
        subtitleIndexes = indexes
        currentSubtitleIndex = player.currentVideoSubTitleIndex
    }

    private func syncState() {
        switch player.state {
        case .opening, .buffering:
            isBuffering = true
        case .playing:
            isBuffering = false
            isPlaying = true
            errorMessage = nil
            UIApplication.shared.isIdleTimerDisabled = true
            if desiredRate != 1 {
                player.rate = desiredRate
            }
        case .paused:
            isBuffering = false
            isPlaying = false
        case .stopped, .ended:
            isBuffering = false
            isPlaying = false
            UIApplication.shared.isIdleTimerDisabled = false
        case .error:
            isBuffering = false
            isPlaying = false
            errorMessage = "无法播放：格式不支持或网络异常"
            UIApplication.shared.isIdleTimerDisabled = false
        @unknown default:
            break
        }
        refreshTracks()
    }

    private func syncTime() {
        currentTime = max(0, Double(player.time.intValue) / 1000)
        if let length = player.media?.length.intValue, length > 0 {
            duration = Double(length) / 1000
        }
    }

    // MARK: - VLCMediaPlayerDelegate

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.syncState()
        }
    }

    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.syncTime()
        }
    }
}
