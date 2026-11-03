# DavPlayer iOS（技术验证阶段）

用 **Kotlin Multiplatform + Compose Multiplatform** 做界面，**播放内核用 MobileVLCKit（Swift 侧）**。
当前验证四件事：

1. CMP 工程能编译、能装到 iPad 上（iPad 2019 / iPadOS 17.7.11）
2. `Ktor + 手写 PROPFIND` 能列出 WebDAV 目录（对齐 `Dav.kt` 的行为）
3. 界面观感与 Android 版一致（深色 + 珊瑚红）
4. **VLCKit 能直接播放 WebDAV 上的视频**（含 Android 端不支持的 RMVB / 10bit 等）

---

## 播放器架构

不是给 Kotlin/Native 做 VLCKit cinterop（大框架绑定的编译与维护成本都高），而是分层：

```
Compose（Kotlin）                      Swift
BrowserScreen ──► PlayerBridge ──► SwiftPlayerPresenter
                    (Kotlin 接口)          │
                                           ▼
                                  PlayerCoordinator (@Published)
                                           │ fullScreenCover
                                           ▼
                                  VLCPlayerScreen (SwiftUI)
                                           │
                                           ▼
                                  VLCPlayerController ──► MobileVLCKit
```

- Kotlin 只负责传 `url / title / user / pass`，认证信息走 VLC 的 `http-user` / `http-pwd` media option，不拼进 URL
- Swift 负责解码、渲染、进度、倍速、字幕轨，`VLCMediaPlayer.drawable` 用 `UIViewRepresentable` 承载
- 退到后台会同时触发隐私锁与关闭播放器，避免全屏播放页盖住计算器

已实现：播放/暂停、进度拖动、0.5x–3x 倍速、内嵌字幕轨切换、缓冲提示、错误重试、控制层 4 秒自动隐藏、退出时停止播放。
待补齐：外挂字幕（需先从 WebDAV 下载）、播放历史与续播、抽帧封面、长按加速。

---

## 目录结构

```
ios/
├── settings.gradle.kts        # 独立构建，不影响根目录的 Android 工程
├── build.gradle.kts           # 插件版本（Kotlin 2.0.21 / CMP 1.7.3）
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/davplayer/app/ios/
│       │   ├── App.kt                    # 根：主题 + 连接页/浏览页切换
│       │   ├── player/PlayerBridge.kt    # ← Swift 播放器的桥接接口
│       │   ├── theme/Theme.kt            # 配色（对齐 Android 版 PulseStream）
│       │   ├── data/DavClient.kt         # Ktor + PROPFIND 解析（对应 Dav.kt）
│       │   └── ui/
│       │       ├── ConnectScreen.kt      # 连接表单（iPad 居中窄栏）
│       │       ├── BrowserScreen.kt      # 浏览页（iPad 双栏 / 手机单列）
│       │       └── Components.kt         # 行、状态页、格式化工具
│       └── iosMain/kotlin/com/davplayer/app/ios/
│           ├── MainViewController.kt
│           └── PrivacyBridge.kt
└── iosApp/
    ├── Podfile                # MobileVLCKit 3.7.5
    ├── project.yml            # xcodegen 工程描述（.xcodeproj 不入库）
    └── iosApp/
        ├── iOSApp.swift       # SwiftUI 壳 + 播放器注册 + fullScreenCover
        ├── Player/
        │   ├── PlayerCoordinator.swift   # 实现 Kotlin PlayerPresenter
        │   ├── VLCPlayerController.swift # VLCMediaPlayer 封装
        │   └── VLCPlayerScreen.swift     # 全屏播放界面
        └── Info.plist         # ATS / 局域网权限 / 横竖屏
```

### iPad 适配

- 宽度 ≥ 840dp 自动切成**双栏**：左侧 380dp 目录树（标题/路径/上级/刷新/换源），右侧详情区（文件名、大小、路径、播放按钮）
- 窄窗口（iPhone / 分屏）保持单列，选中视频后底部出现播放按钮
- 连接页在宽屏居中收窄到 460dp，不再铺满整屏

> `composeApp` 里的 WebDAV 代码是**临时副本**，将来稳定后应抽成 `shared/` 模块，
> 让 Android 和 iOS 共用同一份 `Dav.kt` / `Store.kt`。

---

## 本地构建（需要 macOS + Xcode）

```bash
# 1. Kotlin framework
gradle -p ios :composeApp:linkReleaseFrameworkIosArm64

# 2. 把 framework 放到 Xcode 能找的位置
mkdir -p ios/iosApp/Frameworks
cp -R ios/composeApp/build/bin/iosArm64/releaseFramework/ComposeApp.framework ios/iosApp/Frameworks/

# 3. 生成工程并安装播放器依赖
brew install xcodegen cocoapods
cd ios/iosApp && xcodegen generate && pod install

# 4. 打开 workspace（注意不是 .xcodeproj）
open DavPlayerIos.xcworkspace
```

模拟器调试：

```bash
gradle -p ios :composeApp:linkDebugFrameworkIosSimulatorArm64
```

---

## CI 构建（不需要 Mac）

推送到 `main` 且改动 `ios/**` 时自动触发，或手动 `workflow_dispatch`：

```powershell
gh workflow run ios-ipa
gh run watch
gh run download -n ios-ipa-unsigned
```

CI 步骤：Kotlin framework → xcodegen → `pod install` → `xcodebuild`（workspace，未签名）→ 打包 ipa。
产物是 **未签名** 的 `DavPlayer-unsigned.ipa`。加入 VLCKit 后包体会明显变大（预计 60–80 MB）。

---

## 装到 iPad

**当前目标机：iPad 2019（A10），iPadOS 17.7.11** —— 超过 16.6.1，**不能用 TrollStore**，只能签名安装：

| 方案 | 有效期 | 说明 |
|---|---|---|
| **AltStore / SideStore**（推荐） | 7 天，可自动续签 | Windows 需安装 **Apple 官网版 iTunes**（不是 Microsoft Store 版） |
| Sideloadly | 7 天 | 手动重签，适合偶尔更新 |

步骤：

1. iPad「设置 → 隐私与安全性 → 开发者模式」打开开发者模式
2. Windows 安装 AltServer（需官网版 iTunes），iPad 与电脑同一局域网
3. AltServer 托盘 → Install AltStore → 输入 Apple ID
4. 把 `DavPlayer-unsigned.ipa` 拖进 AltStore 安装（AltStore 会重新签名，VLCKit 这类内嵌 framework 也会一并签）
5. 首次启动如提示「不受信任的开发者」，去「设置 → 通用 → VPN 与设备管理」信任

续签只是重新签名，App 数据（WebDAV 源、播放进度）保留。

---

## 下一步

1. iPad 真机验证：MKV / RMVB / 内嵌 PGS 字幕 / 10bit H.264 / 1080p 流畅度
2. 外挂字幕：浏览同目录 srt/ass/vtt，下载到临时目录再用 `addPlaybackSlave:type:enforce:` 挂载
3. 播放历史与续播（对齐 `Store.kt`）
4. Komga 漫画模块（`Komga.kt` 逻辑可 1:1 翻译）
