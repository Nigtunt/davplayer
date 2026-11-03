# DavPlayer iOS（技术验证阶段）

用 **Kotlin Multiplatform + Compose Multiplatform** 把 Android 版搬到 iOS。
当前只验证三件事：

1. CMP 工程能编译、能装到 iPad 上（iPad 2019 / iPadOS 15~18）
2. `Ktor + 手写 PROPFIND` 能列出 WebDAV 目录（对齐 `Dav.kt` 的行为）
3. 界面观感与 Android 版一致（深色 + 珊瑚红）

播放器（VLCKit）**还没有接入**，这是下一步。

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
│       │   ├── theme/Theme.kt            # 配色（对齐 Android 版 PulseStream）
│       │   ├── data/DavClient.kt         # Ktor + PROPFIND 解析（对应 Dav.kt）
│       │   └── ui/
│       │       ├── ConnectScreen.kt      # 连接表单（iPad 居中窄栏）
│       │       ├── BrowserScreen.kt      # 浏览页（iPad 双栏 / 手机单列）
│       │       └── Components.kt         # 行、状态页、格式化工具
│       └── iosMain/kotlin/com/davplayer/app/ios/
│           └── MainViewController.kt
└── iosApp/
    ├── project.yml             # xcodegen 工程描述（.xcodeproj 不入库）
    └── iosApp/
        ├── iOSApp.swift        # SwiftUI 壳
        └── Info.plist          # ATS / 局域网权限 / 横竖屏
```

### iPad 适配

- 宽度 ≥ 840dp 自动切成**双栏**：左侧 380dp 目录树（标题/路径/上级/刷新/换源），右侧详情区（文件名、大小、路径，播放按钮占位）
- 窄窗口（iPhone / 分屏）保持单列 + 底部提示
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

# 3. 生成工程
brew install xcodegen
cd ios/iosApp && xcodegen generate && open DavPlayerIos.xcodeproj

# 4. Xcode 里选自己的设备 Run（免费 Apple ID 也行，7 天有效）
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

产物是 **未签名** 的 `DavPlayer-unsigned.ipa`（public 仓库的 macOS runner 免费不限时长）。

---

## 装到 iPad

先确认 iPadOS 版本（设置 → 通用 → 关于本机）：

| iPadOS 版本 | 推荐方案 | 有效期 |
|---|---|---|
| ≤ 16.6.1 | **TrollStore**（直接装 ipa，永久） | 永久 |
| 17.x / 18.x | **AltStore / Sideloadly** + 免费 Apple ID | 7 天（可自动续签） |

Windows 上安装 AltStore 需要 **Apple 官网版 iTunes**（不是 Microsoft Store 版本）；
iPadOS 16+ 还必须在「设置 → 隐私与安全性 → 开发者模式」里打开开发者模式。

续签只是重新签名，App 数据（WebDAV 源、播放历史）保留。

---

## 下一步

1. **VLCKit 接入**（`MobileVLCKit`，通过 CocoaPods 加进 `composeApp` 的 iOS 依赖）
   - iPad 上验证：MKV / RMVB 播放、内嵌字幕（含 PGS）、倍速、拖动进度
2. 把浏览/历史/设置改成与 Android 版同构（数据层复用 `Store` 逻辑）
3. Komga 漫画模块（`Komga.kt` 逻辑可 1:1 翻译）
4. iPad 宽屏适配（`WindowSizeClass`，左侧导航 + 双列列表）
