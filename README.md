# DavPlayer

**WebDAV 视频播放器 + Komga 漫画阅读器**（Android），可配合 OpenList / Alist 等 WebDAV 服务与 [Komga](https://komga.org/) 漫画服务器使用。

最新版本：**v2.0.0** · [下载 APK](https://github.com/Nigtunt/davplayer/releases/latest)

---

## 功能特性

### 📺 WebDAV 视频

- **多源配置**：支持添加多个 WebDAV 源（名称 / 地址 / 账号 / 密码），浏览页顶栏一键切换
- **目录浏览**：文件夹与视频文件列表，支持返回上级 / 主目录 / 手动刷新；目录列表内存缓存，返回不重复请求
- **播放历史**：自动记录每个视频的播放进度，历史页查看并从上次位置续播（接近播完自动归零重看）
- **封面抽帧**：开始播放后自动从渲染画面抽帧作为列表/历史封面；未播放不产生任何请求或缓存
- **进度展示**：浏览列表与历史记录均显示缩略图 + 底部渐变进度条

### 🎬 播放器

- **倍速播放**：0.5x–3x 循环切换；**长按屏幕**任意位置临时加速（默认 2x，可配置 1.5x–4x），松开恢复
- **手势操作**：单击显示/隐藏控制栏；**横向拖动**调整进度（满屏 ≈ 120s，中央实时预览）
- **字幕**：支持外挂 srt / ass / ssa / vtt，选择器默认打开视频所在同级目录
- **全屏**：按键切换横屏沉浸式全屏
- **解码切换**：硬件优先（默认）/ 软件优先，可修复部分视频（如 10bit H.264）硬解卡顿
- 加大缓冲策略（15s/60s），对抗 WebDAV 网络抖动

### 📚 Komga 漫画

- **书库浏览**：继续阅读、最新入库、阅读列表、书库分区首页
- **系列详情**：书籍列表 + 阅读进度，返回列表时单独刷新刚读那本（整页不闪烁）
- **阅读列表**：每个书库的阅读列表浏览，支持新建 / 删除列表、添加 / 移除漫画
- **阅读器**：左→右 / 右→左 / 竖向滚动三种模式，双指缩放、双击放大、左右点击翻页
- **进度条**：点击屏幕呼出底部进度条，拖动只变数字、松手跳转；滑动时显示页码浮层
- **进度同步**：本地历史 + Komga 云端 read-progress 双向记录

### 🔐 隐私与缓存

- 漫画封面/页图以 **AES-256-GCM 加密**保存到本地（密钥托管于 Android KeyStore）
- 列表展示走加密缓存（秒开不闪烁），进入详情后台刷新一次
- 设置页可查看缓存占用、一键清空、查看最近请求与缓存日志

### 🎨 界面（v2.0 PulseStream UI）

- YouTube 邃黑 / Ins 纯白**双主题**（可跟随系统）
- 珊瑚霓虹红 `#FF0050` 主色 + 品牌渐变进度条（`#FF0050→#FF5B2E→#FF00A0`）
- 紧凑小图列表、胶囊筛选、分组卡片式设置页、播放器渐变蒙层

---

## 技术栈

| 领域 | 方案 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) |
| 播放内核 | Media3 / ExoPlayer（OkHttp DataSource，自定义 MediaCodecSelector 软硬解切换） |
| WebDAV | OkHttp 手写 PROPFIND 请求 + XmlPullParser 解析 |
| 漫画接口 | Komga REST API（Basic 认证，OpenAPI v3） |
| 图片加载 | Coil + 自定义 Fetcher/Keyer（加密缓存解密直出 Bitmap） |
| 加密 | AES-256-GCM + Android KeyStore |
| 本地存储 | JSON 文件（播放/阅读历史）+ SharedPreferences（配置） |

## 构建

**环境要求**：JDK 17+，Android SDK（platform-34，build-tools 34.0.0）

```bash
# 本地构建
gradle assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

**CI**：推送到 `main` 分支后 GitHub Actions 自动构建 debug APK，可在 Actions 运行详情的 Artifacts 中下载。

**系统要求**：Android 7.0（API 24）及以上。

## 目录结构

```
app/src/main/java/com/davplayer/app/
├── MainActivity.kt     # 浏览 / 历史 / 设置 / 主题与导航
├── PlayerActivity.kt   # 视频播放器（手势、倍速、字幕、全屏、抽帧封面）
├── ReaderActivity.kt   # 漫画阅读器（三种模式、缩放、进度跳转）
├── KomgaUi.kt          # Komga 书架 / 系列 / 阅读列表界面
├── Komga.kt            # Komga API 客户端
├── Dav.kt              # WebDAV 客户端（PROPFIND）
├── SecureImage.kt      # 加密图片缓存 + Coil 集成
├── Store.kt            # 配置 / 历史存储
├── Theme.kt            # PulseStream 双主题设计令牌
└── Log.kt              # 运行日志（内存环形缓冲 + HTTP 拦截器）
```

## License

MIT
