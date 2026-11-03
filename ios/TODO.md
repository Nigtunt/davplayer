# iOS 端 TODO

> 状态：VLCKit 播放器已接入，待 iPad 真机验证。
> 最后更新：2026-11-03

## 已完成

- [x] KMP + Compose Multiplatform 工程骨架（`ios/`，独立 Gradle 构建，不影响 Android）
- [x] Ktor PROPFIND WebDAV 客户端（`data/DavClient.kt`，对应 Android `Dav.kt`）
- [x] 连接页（宽屏居中收窄）
- [x] 浏览页：iPad ≥840dp 双栏（左目录树 + 右详情），窄屏单列
- [x] SwiftUI 壳 + xcodegen 工程 + Info.plist（ATS / 局域网权限 / 横竖屏）
- [x] CI：`.github/workflows/ios.yml` 产出未签名 ipa
- [x] 隐私模式（计算器暗码 19981212，默认关闭）— 与 Android 同步实现
- [x] **接入 MobileVLCKit 3.7.5**（CocoaPods + xcframework，Swift 侧播放，不做 Kotlin cinterop）
- [x] 播放页：全屏 SwiftUI 控制层，`UIViewRepresentable` 承载 `VLCMediaPlayer.drawable`
- [x] 播放/暂停、进度拖动、0.5x–3x 倍速、内嵌字幕轨切换、缓冲提示、错误重试
- [x] 全屏退出/退后台停止播放，避免绕过隐私锁
- [x] 浏览页播放入口：iPad 详情区按钮、窄屏底部播放按钮（带 Basic 认证）

## 待办：真机验证（最高优先级）

> iPad 2019（A10），iPadOS 17.7.11，只能 AltStore/Sideloadly 签名安装（7 天续签）。

- [ ] 装包与开发者模式设置
- [ ] MKV（H.264 + AAC）播放
- [ ] RMVB 播放（Android 端不支持，iOS 靠 VLC 应该可以）
- [ ] 内嵌字幕（含 PGS 图形字幕）显示与切换
- [ ] 10bit H.264 / 高码率 1080p 在 A10 上的流畅度
- [ ] WebDAV Basic 认证（Alist/OpenList）实际连通
- [ ] 横竖屏切换、AirPlay/外接屏表现
- [ ] 长时间播放的内存占用（VLCKit 与 CMP 共存）

## 待办：播放器补齐

- [ ] 外挂字幕：同目录 srt/ass/ssa/vtt，下载后 `addPlaybackSlave:type:enforce:`
- [ ] 播放历史与续播（`NSUserDefaults` / JSON，对齐 `Store.kt`）
- [ ] 抽帧做封面（VLCKit snapshot API）
- [ ] 长按屏幕临时加速（对齐 Android 2x）
- [ ] 音轨切换（`audioTrackNames` / `audioTrackIndexes`）
- [ ] 网络缓冲参数按实际网络调优（当前 `network-caching=2000`）

## 待办：功能对齐

- [ ] 浏览页：多源管理、目录缓存、返回上级保留滚动位置
- [ ] Komga：书架 / 系列 / 阅读列表（`Komga.kt` 逻辑可 1:1 翻译）
- [ ] 漫画阅读器：左→右 / 右→左 / 竖向、双指缩放、双击放大
- [ ] 加密图片缓存：CryptoKit AES-GCM + Keychain 存密钥
- [ ] 深浅主题跟随系统
- [ ] App 图标（`Assets.xcassets`，目前装出来是白图标）

## 待办：工程化

- [ ] 把 `data/DavClient.kt` 与 Android 的 `Dav.kt` 合并成 `shared/` 模块，避免两份实现
- [ ] CI 缓存 Kotlin/Native 工具链与 CocoaPods（首次构建 ~12min + VLCKit 下载）
- [ ] iPad 分屏 / Stage Manager 下的宽度适配（当前只按 840dp 断点）
- [ ] 连接页增加"最近使用的源"
- [ ] 隐私模式：iOS 端靠 `UIApplicationDidEnterBackgroundNotification` 上锁，需真机验证

## 安装方式（已确认）

- iPad 2019（iPad 7，A10，最高 iPadOS 18），当前系统 **17.7.11**
- 系统 > 16.6.1，**TrollStore 不可用**
- **AltStore / SideStore**（推荐，可自动续签）或 Sideloadly，免费 Apple ID，7 天
- Windows 需官网版 iTunes；首次装前在 iPad 打开「设置 → 隐私与安全性 → 开发者模式」

## 已知限制

- iOS 用 VLCKit 后**支持 RMVB / WMV / PGS 字幕**，比 Android 版强
- 包体会从 8.95MB 涨到 **60–80MB**（VLCKit 体积），7 天续签时要重新上传
- 无法实现"应用内下载安装更新"，只能提示新版本 + 跳转链接
- 自签名 HTTPS（自建 WebDAV）VLC 可能校验证书失败，需要有效证书或改用 HTTP
