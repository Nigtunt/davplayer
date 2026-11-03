# iOS 端 TODO

> 状态：技术验证阶段。Android 端已发版 v2.1.1，iOS 仅有骨架（浏览 + iPad 双栏）。
> 最后更新：2026-11-03

## 已完成

- [x] KMP + Compose Multiplatform 工程骨架（`ios/`，独立 Gradle 构建，不影响 Android）
- [x] Ktor PROPFIND WebDAV 客户端（`data/DavClient.kt`，对应 Android `Dav.kt`）
- [x] 连接页（宽屏居中收窄）
- [x] 浏览页：iPad ≥840dp 双栏（左目录树 + 右详情），窄屏单列
- [x] SwiftUI 壳 + xcodegen 工程 + Info.plist（ATS / 局域网权限 / 横竖屏）
- [x] CI：`.github/workflows/ios.yml` 产出未签名 ipa（run 41602904283 通过，8.95MB arm64）
- [x] 隐私模式（计算器暗码 19981212，默认关闭）— 与 Android 同步实现

## 待办：播放器（最高优先级）

- [ ] 接入 **MobileVLCKit**（CocoaPods 加入 `composeApp` 的 ios 依赖，或用 SwiftPM + cinterop）
- [ ] 播放页：`UIViewRepresentable` 包 `VLCVideoView`，支持横屏全屏
- [ ] 验证清单（决定 iOS 版是否成立）：
  - [ ] MKV（H.264 + AAC）播放
  - [ ] RMVB 播放（Android 端不支持，iOS 靠 VLC 应该可以）
  - [ ] 内嵌字幕（含 PGS 图形字幕）
  - [ ] 外挂 srt/ass 字幕
  - [ ] 倍速 0.5–3x、拖动进度、长按加速
  - [ ] 1080p 在 iPad 2019（A10）上的流畅度
- [ ] 抽帧做封面（VLCKit snapshot API）

## 待办：功能对齐

- [ ] 播放历史与续播（`NSUserDefaults` / JSON 文件，对齐 `Store.kt`）
- [ ] 浏览页：多源管理、目录缓存、返回上级保留滚动位置
- [ ] 字幕面板：内嵌字幕轨切换 + 从 WebDAV 选外挂字幕
- [ ] 不支持格式提示 + 外部播放器（iOS 用 `UIActivityViewController` 或 `openURL`）
- [ ] Komga：书架 / 系列 / 阅读列表（`Komga.kt` 逻辑可 1:1 翻译）
- [ ] 漫画阅读器：左→右 / 右→左 / 竖向、双指缩放、双击放大
- [ ] 加密图片缓存：CryptoKit AES-GCM + Keychain 存密钥
- [ ] 深浅主题跟随系统
- [ ] App 图标（`Assets.xcassets`，目前装出来是白图标）

## 待办：工程化

- [ ] 把 `data/DavClient.kt` 与 Android 的 `Dav.kt` 合并成 `shared/` 模块，避免两份实现
- [ ] CI 缓存 Kotlin/Native 工具链（首次 12min，后续应该更快，可显式缓存 `~/.konan`）
- [ ] iPad 分屏 / Stage Manager 下的宽度适配（当前只按 840dp 断点）
- [ ] 结构梳理：连接页增加"最近使用的源"
- [ ] 隐私模式：iOS 端目前靠 `UIApplicationDidEnterBackgroundNotification` 上锁，需真机验证

## 安装方式（等确认 iPad 版本）

- iPad 2019（iPad 7，A10，最高 iPadOS 18）
- 若系统 **≤ 16.6.1**：TrollStore，永久有效
- 若 **17.x / 18.x**：AltStore / Sideloadly + 免费 Apple ID，7 天续签（Windows 需官网版 iTunes）
- 首次装前记得在 iPad 打开「设置 → 隐私与安全性 → 开发者模式」

## 已知限制（与 Android 版一致或更强）

- iOS 用 VLCKit 后**支持 RMVB / WMV / PGS 字幕**，比 Android 版强
- 包体会从 8.95MB 涨到 **80MB+**（VLCKit 体积）
- 无法实现"应用内下载安装更新"，只能提示新版本 + 跳转链接
