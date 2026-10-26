# 发版 · 签名 · 应用内更新

> 适用版本：v2.1.0 及以后。涉及文件：`app/build.gradle.kts`、`.github/workflows/build.yml`、`Update.kt`、`AndroidManifest.xml`。

---

## 1. 签名密钥

### 1.1 密钥放在哪

| 位置 | 内容 | 是否入库 |
|---|---|---|
| 本地 `keystore/davplayer.jks` | 签名密钥库 | 否（已 gitignore） |
| 本地 `keystore/keystore.properties` | 密码 / 别名 | 否（已 gitignore） |
| GitHub Secrets | jks 的 base64 + 三个签名参数 | 否 |

- 别名：`davplayer`
- 有效期：10950 天（约 30 年）
- 构建时 `app/build.gradle.kts` 的优先级：**环境变量** > `keystore/keystore.properties` > 默认值（别名），密钥文件路径由 `DAV_KEYSTORE_PATH` 覆盖，默认 `keystore/davplayer.jks`
- 找不到密钥时回退到默认 debug 签名（仅本地调试，**不能用于发版**，否则用户装不上更新）

### 1.2 一次性配置 GitHub Secrets

仓库 → Settings → Secrets and variables → Actions → New repository secret，建立 4 个：

| Secret 名称 | 值 |
|---|---|
| `KEYSTORE_BASE64` | `keystore/davplayer.jks` 的 base64（单行，见下） |
| `KEYSTORE_PASSWORD` | `keystore.properties` 里的 `storePassword` |
| `KEY_ALIAS` | `davplayer` |
| `KEY_PASSWORD` | `keystore.properties` 里的 `keyPassword` |

生成 base64（Windows PowerShell，直接进剪贴板）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\project\davplayer\keystore\davplayer.jks")) | Set-Clipboard
```

macOS / Linux：

```bash
base64 -w0 keystore/davplayer.jks            # Linux
base64 -i keystore/davplayer.jks | tr -d '\n' # macOS
```

CI 在构建前把 `KEYSTORE_BASE64` 解码回 `keystore/davplayer.jks`，密码通过环境变量
`DAV_STORE_PASSWORD` / `DAV_KEY_ALIAS` / `DAV_KEY_PASSWORD` 传给 Gradle。

也可以直接用 gh CLI 一次配齐（注意：一定要用 `--body`，用管道会带 CRLF 换行，导致 CI 里 `base64 -d` 报 invalid input）：

```powershell
gh secret set KEYSTORE_BASE64 --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\project\davplayer\keystore\davplayer.jks")))
gh secret set KEYSTORE_PASSWORD --body "davplayer"
gh secret set KEY_ALIAS --body "davplayer"
gh secret set KEY_PASSWORD --body "davplayer"
```

### 1.3 换机器 / 换协作者（重要）

GitHub Secrets **只能写入、不能读回**。所以密钥一定要自己另存一份，否则换台电脑就再也拿不到同一把钥匙了。

推荐把这俩存进密码管理器 / 加密网盘 / 私有仓库（别放公开仓库）：

- `keystore/davplayer.jks` 文件本身
- 它的 base64 字符串（等价于 jks 内容）

在新机器上恢复：

```powershell
New-Item -ItemType Directory -Force keystore

# 方式一：直接拷 jks 过来；方式二：从 base64 还原
[IO.File]::WriteAllBytes(
  "D:\project\davplayer\keystore\davplayer.jks",
  [Convert]::FromBase64String("<粘贴 base64>"))

# 写密码配置（内容与仓库 Secrets 保持一致）
@"
storePassword=davplayer
keyAlias=davplayer
keyPassword=davplayer
"@ | Set-Content keystore\keystore.properties
```

也可以不写 properties，改用环境变量：`DAV_KEYSTORE_PATH`、`DAV_STORE_PASSWORD`、`DAV_KEY_ALIAS`、`DAV_KEY_PASSWORD`。

**没有密钥的机器上构建会怎样？**

- 构建仍会成功，但自动回退到该机器的 `~/.android/debug.keystore`（**每台机器随机生成，签名各不相同**），配置阶段会打印一条 warn 提示
- 这样打出的包**无法覆盖安装**到已发布的正式版（系统提示「应用未安装」/ 签名冲突）；装了这个包之后再点应用内更新，也会被签名校验拦下
- 只适合“改代码 + 本机调试”，或先卸载再装
- CI 不受影响：它从 Secrets 还原密钥，签名始终一致

### 1.4 重新生成密钥（慎用）

```bash
keytool -genkeypair -v -keystore keystore/davplayer.jks -alias davplayer \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass <密码> -keypass <密码> \
  -dname "CN=DavPlayer,OU=Dev,O=DavPlayer,C=CN"
```

换密钥意味着**所有已安装用户都无法覆盖升级**（系统会拒绝，只能卸载重装），除非换 applicationId 当成新应用发布。

---

## 2. 版本号规则

`app/build.gradle.kts` → `defaultConfig`：

- `versionCode`：**每次发版必须 +1**，且不能倒退（否则系统按降级拒绝安装）
- `versionName`：与 GitHub Release 的 tag 对应，例如 `2.1.0` ↔ `v2.1.0`

应用内更新只在 `versionName` 变大时才提示；能否真正装上则看 `versionCode` 和签名。

---

## 3. 发版流程

1. 改 `versionCode` / `versionName`，必要时更新 README 特性说明
2. 提交并 push 到 `main` → GitHub Actions 自动构建 **release** 包（Artifacts 里的 `DavPlayer-release-apk`，文件名为 `DavPlayer-vX.Y.Z.apk`）
3. 新建 Release：tag 用 `vX.Y.Z`，上传该 apk（文件名任意，**必须以 `.apk` 结尾**），填写更新说明（正文会原样显示在应用内弹窗里）
4. 用户端：
   - 自动：启动时若距上次检查超过 24 小时会静默检查，有新版弹窗
   - 手动：「我的」→「关于 · 更新」→「检查更新」

本地出包：

```powershell
cd D:\project\davplayer

# 发版：release 包（非 debuggable，体积更小）
.\gradlew.bat :app:assembleRelease
# 产物 app\build\outputs\apk\release\app-release.apk

# 调试：debug 包（可挂调试器，仅自己用）
.\gradlew.bat :app:assembleDebug
# 产物 app\build\outputs\apk\debug\app-debug.apk
```

> 两者签名相同，可以互相覆盖安装。正式分发一律用 release 包。

---

## 4. 应用内更新实现

全部逻辑在 `Update.kt`：

| 组件 | 作用 |
|---|---|
| `Updater.checkLatest` | `GET https://api.github.com/repos/Nigtunt/davplayer/releases/latest`，取 `tag_name` 与当前 `versionName` 逐段比较，再取 `assets` 里第一个 `.apk` 的 `browser_download_url` |
| `Updater.download` | OkHttp 流式下载到 `getExternalFilesDir(null)/apk/`，回调 0~1 进度 |
| `Updater.isSameSignature` | `getPackageArchiveInfo` 读下载包签名，与本机签名比对，不一致就拦下并提示 |
| `Updater.canInstallPackages` / `openInstallPermission` | Android 8+ 的「安装未知应用」权限检查与跳转 |
| `Updater.install` | `FileProvider` 生成 `content://` 地址 + `ACTION_VIEW` 调起系统安装器 |
| `UpdateState` | 检查/下载状态（checking、progress、message、error），设置页与启动检查共用 |
| `UpdateDialog` | 版本号 + Release 说明（可滚动）+ 下载进度 + 立即更新 |

其他约定：

- **节流**：`SharedPreferences("settings")` 里 `lastUpdateCheck` 记录时间戳，24 小时查一次
- **不打扰**：点「稍后」写入 `skippedUpdateVersion`，同一版本不再自动弹（手动点检查更新仍会弹）
- **日志**：`AppLog` tag `UPDATE`，「我的 → 查看日志」可见
- **权限**：`AndroidManifest.xml` 声明 `REQUEST_INSTALL_PACKAGES` 与 `FileProvider`（`${applicationId}.fileprovider`，路径见 `res/xml/file_paths.xml`）

### 改仓库地址

`Update.kt` 顶部：

```kotlin
private const val REPO = "Nigtunt/davplayer"
```

---

## 5. 排查

| 现象 | 原因 / 处理 |
|---|---|
| 检查更新提示「仓库还没有发布 Release」 | 404：还没有 Release，或仓库名写错，或仓库是私有的 |
| 提示「安装包签名与当前应用不一致」 | 发布包与本机安装包签名不同：检查 Secrets 里的 jks 是否与本地 `keystore/davplayer.jks` 同一把 |
| 点安装后弹「应用未安装」 | `versionCode` 没有递增（降级），或签名不一致 |
| 下载卡住 / 失败 | GitHub 下载域名被网络限制；日志 tag `UPDATE` 有具体错误 |
| CI 报「缺少 secret KEYSTORE_BASE64」 | 没配 Secret，见 1.2 |
| CI 的 Verify signature 步骤失败 | 还原的 jks 与密码不匹配，或 `KEY_ALIAS` 填错 |
| 检查更新频率异常 | 手动清理 `SharedPreferences` 的 `lastUpdateCheck` 后重试 |

---

## 6. 已知格式限制（与更新无关，记录备查）

- `rmvb` / `rm` / `wmv` / `asf`：Media3 没有对应解封装器，内置播放器无法播放，只能走「外部播放器」
- `mkv` 里带 DTS / TrueHD / AC3 音轨：手机通常没有这些解码器，表现为有画面无声音
- `avi`（DivX/Xvid）、`mpg`/`vob`（MPEG-2）：容器能解析但多数新机型没有对应的视频解码器
- `ass` / `ssa` 外挂字幕：仅按纯文本 + 基础样式渲染，特效定位不还原

---

## 7. v2.1.0 变更摘要

- 浏览目录：每个目录独立记住滚动位置，返回上级不再回到顶部
- 播放：`onConfigurationChanged` 里横屏自动沉浸式（隐藏状态栏/导航栏），竖屏自动恢复
- 播放失败：新增错误覆盖层（原因 + 重试 / 外部播放器 / 返回），日志 tag `PLAYER`
- 不支持格式：`rmvb/rm/wmv/asf` 提前拦截并提示，列表里格式标签标红
- 列表：长名称可点「展开全名」，不再强制省略
- 历史：长按视频 → 跳转到视频所在目录 / 删除记录
- 新增：应用内检查更新（GitHub Releases）
- 构建：CI 改为出 **release** 包（非 debuggable，体积 19.2MB → 13.6MB），debug 包仅本地调试
- 签名：密钥迁到 GitHub Secrets，本地/CI 同签名，支持覆盖升级
