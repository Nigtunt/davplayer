# 项目状态与交接说明

> 最后更新：2026-11-03 · 对应版本 **Android v2.2.0（versionCode 5）**

---

## 1. 当前状态

| 端 | 状态 | 产物 |
|---|---|---|
| **Android** | 已发布 v2.2.0，功能完整 | GitHub Release 里的 `DavPlayer-v2.2.0.apk`（13.58 MB，release 签名，非 debuggable） |
| **iOS** | VLCKit 播放器已接入（浏览 + 播放 + 隐私模式），待真机验证，未发布 | CI 产出未签名 `DavPlayer-unsigned.ipa`（含 VLCKit 后约 60–80 MB，需自签安装） |

构建全部走 GitHub Actions：
- `.github/workflows/build.yml` → Android release APK（推 main 自动触发）
- `.github/workflows/ios.yml` → iOS 未签名 ipa（改动 `ios/**` 或手动触发）

---

## 2. 本轮（v2.1.0 → v2.2.0）完成的改动

| # | 改动 | 关键文件 |
|---|---|---|
| 1 | 浏览目录独立记忆滚动位置，返回上级不回顶部 | `MainActivity.kt`（`listStates` / `remember(path)`） |
| 2 | 横屏自动沉浸式全屏，竖屏自动恢复 | `PlayerActivity.kt`（`onConfigurationChanged` / `applyImmersive`） |
| 3 | 播放失败显示原因（重试 / 外部播放器 / 返回），不再卡缓冲 | `PlayerActivity.kt`（`onPlayerError` + 覆盖层） |
| 4 | RMVB / RM / WMV / ASF 提前拦截并提示 | `Dav.kt`（`UNSUPPORTED_EXTS`）+ 列表红标 |
| 5 | 长名称可展开显示完整名称 | `MainActivity.kt`（`ExpandableName`） |
| 6 | 历史长按 → 跳转到视频所在目录 / 删除 | `MainActivity.kt`（`combinedClickable` + `DropdownMenu`） |
| 7 | **应用内检查更新**（GitHub Releases → 下载 → 调起安装） | `Update.kt`（`Updater` / `UpdateState` / `UpdateDialog`） |
| 8 | 固定签名 + 密钥入库 GitHub Secrets | `app/build.gradle.kts`、`docs/RELEASE.md` |
| 9 | CI 改产 release 包（19.2MB → 13.6MB） | `.github/workflows/build.yml` |
| 10 | **修复内嵌字幕不显示**（补 SubtitleView 渲染 + 自动选轨 + 字幕面板） | `PlayerActivity.kt` |
| 11 | iOS CMP 骨架 + iPad 双栏 | `ios/**`、`ios/TODO.md` |
| 12 | **隐私模式**（伪装计算器，暗码 19981212） | `Privacy.kt`、`MainActivity.kt`、`ios/.../CalculatorScreen.kt` |

### 2.1 本轮 iOS 播放器接入（未发版）

| # | 改动 | 关键文件 |
|---|---|---|
| 1 | 接入 **MobileVLCKit 3.7.5**（CocoaPods，xcframework） | `ios/iosApp/Podfile`、`.github/workflows/ios.yml` |
| 2 | Kotlin→Swift 播放桥（不做 cinterop，Kotlin 只传 URL/账号） | `ios/composeApp/.../player/PlayerBridge.kt` |
| 3 | 全屏播放页：播放/暂停、拖动、倍速、内嵌字幕、缓冲/错误处理 | `ios/iosApp/iosApp/Player/*.swift` |
| 4 | 浏览页播放入口（iPad 详情区 / 窄屏底部按钮），Basic 认证走 media option | `BrowserScreen.kt`、`VLCPlayerController.swift` |
| 5 | 退后台同时退出播放器，避免盖住隐私锁 | `iOSApp.swift` |

> 目标机已确认 **iPad 2019 / iPadOS 17.7.11**：不支持 TrollStore，只能 AltStore/Sideloadly + 免费 Apple ID（7 天续签）。

---

## 3. 隐私模式说明（v2.2.0 新增）

- 开关：Android「我的 → 隐私 → 隐私模式」；iOS 连接页/浏览页「设置」
- 默认关闭；开启后启动只显示计算器，输入 `19981212` 按 `=` 才进入应用
- 计算器是**真的能算**（四则运算 / 百分号 / 正负号 / 退格 / 小数点），输错不会有任何提示
- 整个应用退到后台会**重新上锁**；应用内进播放器/阅读器不会误锁
- Android 播放器/阅读器在 `onResume` 检查 `PrivacyGate.locked`，锁定时 `finish()`，防止后台返回绕过

---

## 4. 待办

### Android（优先级低，不影响使用）

- [ ] 音频扩展名（mp3/flac/m4a/aac/ogg）加入浏览列表
- [ ] MKV 带 DTS / TrueHD 音轨时提示"视频可播但音轨不支持"
- [ ] 可选：打开 R8（`isMinifyEnabled` + `shrinkResources`），体积还能再降几 MB
- [ ] CI 里的 Actions 弃用警告（`setup-java@v4` → v5、Node20 → Node24）

### iOS（详见 `ios/TODO.md`）

- [x] 接入 MobileVLCKit 3.7.5 + 全屏播放器
- [ ] **iPad 真机验证**（MKV / RMVB / PGS 字幕 / 1080p 流畅度）——最高优先级
- [ ] 外挂字幕（下载后 addPlaybackSlave）、播放历史与续播
- [ ] Komga 与漫画阅读器
- [ ] App 图标（`Assets.xcassets`，目前是白图标）
- [x] 安装方式确认：iPadOS **17.7.11**，TrollStore 不可用 → AltStore/Sideloadly（7 天）
- [ ] 把 `ios/.../data/DavClient.kt` 与 Android 的 `Dav.kt` 合并成 `shared/` 模块

---

## 5. 切换开发环境必读

### 5.1 必须手动带走的东西（不在仓库里）

| 文件 | 作用 | 丢失后果 |
|---|---|---|
| `keystore/davplayer.jks` | 签名密钥 | **无法再覆盖升级**，只能让所有用户卸载重装 |
| `keystore/keystore.properties` | 签名密码（`storePassword` / `keyAlias` / `keyPassword`） | 同上 |
| `local.properties` | Android SDK 路径（`sdk.dir=D:\\Android\\Sdk`） | 本地构建报找不到 SDK |

> 建议把 `keystore/` 两个文件存进密码管理器。GitHub Secrets 只能写入、不能读回。
> Secrets 名称：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。

### 5.2 新机器环境准备

1. 安装 **JDK 17+**、**Android SDK**（platform-34、build-tools 34.0.0）、**Git**
2. clone 仓库后新建 `local.properties`：
   ```properties
   sdk.dir=D:\\Android\\Sdk
   ```
3. 从密码管理器恢复 `keystore/` 两个文件
4. 安装 GitHub CLI 并登录（发版要用）：`gh auth login`
5. 网络：本机直连 `github.com:443` 会超时，需走代理；`git push` 可用：
   ```powershell
   git -c http.proxy=http://127.0.0.1:7897 -c https.proxy=http://127.0.0.1:7897 push origin main
   ```
   （`api.github.com` 直连可用，`gh` 不需要代理。）

### 5.3 常用命令

```powershell
# 编译 release（发版用）
.\gradlew.bat :app:assembleRelease
# 产物 app\build\outputs\apk\release\app-release.apk

# 本地调试包
.\gradlew.bat :app:assembleDebug

# iOS（需要 macOS）
gradle -p ios :composeApp:linkReleaseFrameworkIosArm64

# 校验签名
D:\Android\Sdk\build-tools\34.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk
```

发版流程见 **`docs/RELEASE.md`**（版本号规则、Secrets 配置、应用内更新原理、故障排查）。

---

## 6. 版本历史

| 版本 | versionCode | 内容 | 状态 |
|---|---|---|---|
| 2.0.0 | 2 | PulseStream UI 重构 | 已发布 |
| 2.1.0 | 3 | 浏览/播放修复 + 应用内更新 + 固定签名 | 未单独发布（并入 2.1.1） |
| 2.1.1 | 4 | 内嵌字幕渲染修复 | 已发布 |
| 2.2.0 | 5 | 隐私模式（计算器锁屏） | 已发布 |

---

## 7. 已知限制

- `rmvb` / `rm` / `wmv` / `asf`：Android 内置播放器不支持（Media3 无解封装），只能外部播放器；iOS 接入 VLCKit 后可原生支持
- MKV 里 DTS / TrueHD / AC3 音轨：手机通常无解码器，表现为有画面无声音
- PGS / VobSub 图形字幕：Android 无法渲染，iOS（VLCKit）可以
- ASS / SSA：仅基础样式渲染，特效不还原
- 应用内更新只对 GitHub 渠道有效；iOS 无法自更新
