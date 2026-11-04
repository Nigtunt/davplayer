import AVFoundation
import Combine
import Foundation
import MobileVLCKit
import UIKit
import ComposeApp

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

    /// 进度回调：position / duration（秒）/ 本地封面路径
    var onProgress: ((Double, Double, String?) -> Void)?

    private var mediaURL: URL?
    private var httpUser = ""
    private var httpPass = ""
    private var desiredRate: Float = 1
    private var pendingSeekMs: Int64 = 0
    private var coverPath: String?
    private var coverAttempted = false
    private var lastReportAt: TimeInterval = 0
    private var resolveTask: URLSessionDataTask?

    override init() {
        super.init()
        videoView.backgroundColor = .black
        videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        player.delegate = self
        player.drawable = videoView
    }

    // MARK: - 生命周期

    func start(url: URL, user: String, pass: String, startMs: Int64 = 0) {
        mediaURL = url
        httpUser = user
        httpPass = pass
        pendingSeekMs = startMs
        coverPath = nil
        coverAttempted = false
        lastReportAt = 0
        activateAudioSession()

        // OpenList/AList 播放网盘文件时会 302 到网盘直链。
        // VLC 自己跟随时不一定会带对 UA/鉴权；这里先用 URLSession 跟到最终地址再交给 VLC。
        resolveTask?.cancel()
        resolveRedirect(url: url, user: user, pass: pass) { [weak self] finalURL in
            DispatchQueue.main.async {
                guard let self else { return }
                if let finalURL {
                    self.mediaURL = finalURL
                }
                self.playCurrent()
            }
        }
    }

    func retry() {
        playCurrent()
    }

    func stop() {
        resolveTask?.cancel()
        resolveTask = nil
        // 退出前再报一次最终进度
        onProgress?(currentTime, duration, coverPath)
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
        var options: [AnyHashable: Any] = [
            "network-caching": 2000,
            // 和 Android 端一样带浏览器 UA，部分网盘直链会校验 UA
            "http-user-agent": "Mozilla/5.0 (iPad; CPU OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "http-referrer": url.absoluteString
        ]
        if !httpUser.isEmpty {
            options["http-user"] = httpUser
            options["http-pwd"] = httpPass
        }
        media.addOptions(options)

        player.media = media
        player.play()
    }

    /// 跟随 302，拿到 OpenList 后面的网盘直链
    private func resolveRedirect(
        url: URL,
        user: String,
        pass: String,
        completion: @escaping (URL?) -> Void
    ) {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.httpMethod = "HEAD"
        request.setValue(
            "Mozilla/5.0 (iPad; CPU OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            forHTTPHeaderField: "User-Agent"
        )
        if !user.isEmpty {
            let token = Data("\(user):\(pass)".utf8).base64EncodedString()
            request.setValue("Basic \(token)", forHTTPHeaderField: "Authorization")
        }

        let task = URLSession.shared.dataTask(with: request) { _, response, error in
            let final = (response as? HTTPURLResponse)?.url
            if let error {
                PlayerBridge.shared.log(message: "重定向解析失败：\(error.localizedDescription)")
            } else {
                PlayerBridge.shared.log(
                    message: "重定向：\(url.host ?? "?") -> \(final?.host ?? "same")"
                )
            }
            completion(final)
        }
        resolveTask = task
        task.resume()
    }

    /// 外挂字幕（WebDAV 上的 srt/ass/ssa/vtt）
    func loadSubtitle(_ url: String) {
        guard let parsed = URL(string: url) else { return }
        player.addPlaybackSlave(parsed, type: .subtitle, enforce: true)
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
            if pendingSeekMs > 0 {
                let target = Double(pendingSeekMs) / 1000.0
                pendingSeekMs = 0
                player.time = VLCTime(int: Int32(target * 1000))
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
        // 某些流上 state 回调只停在 buffering，但时间已经在走；以 isPlaying 为准修正 UI 状态
        if player.isPlaying {
            if isBuffering { isBuffering = false }
            if !isPlaying { isPlaying = true }
        }

        currentTime = max(0, Double(player.time.intValue) / 1000)
        if let length = player.media?.length.intValue, length > 0 {
            duration = Double(length) / 1000
        }

        let now = Date().timeIntervalSince1970
        if player.isPlaying && now - lastReportAt >= 5 {
            lastReportAt = now
            onProgress?(currentTime, duration, coverPath)
        }

        if !coverAttempted && player.isPlaying && currentTime >= 2.5 {
            coverAttempted = true
            generateCover()
        }
    }

    private func generateCover() {
        guard let mediaURL else { return }
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("covers", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let name = String(abs(mediaURL.absoluteString.hashValue))
        let path = dir.appendingPathComponent("\(name).jpg").path
        if !FileManager.default.fileExists(atPath: path) {
            player.saveVideoSnapshot(at: path, withWidth: 320, andHeight: 180)
        }
        if FileManager.default.fileExists(atPath: path) {
            coverPath = path
            onProgress?(currentTime, duration, coverPath)
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
